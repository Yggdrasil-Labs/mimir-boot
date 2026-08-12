#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$project_dir/scripts/verify-task-commits.sh"

fixture_dir=""
fixture_seed=""
declare -a task_shas=()
supplemental_sha=""
baseline_sha=""
implementation_head_sha=""
out_of_range_sha=""

cleanup() {
  if [[ -n "$fixture_dir" ]]; then
    rm -rf "$fixture_dir"
  fi
}

cleanup_all() {
  cleanup
  [[ -z "$fixture_seed" ]] || rm -rf "$fixture_seed"
}
trap cleanup_all EXIT

expect_failure() {
  if "$@"; then
    echo "预期命令失败但实际成功: $*" >&2
    return 1
  fi
}

write_plan() {
  local t10_red_result
  t10_red_result='{"command":"fixture","exitCode":0,"failsafeTests":1,"failureKind":"none","failedContracts":[],"runtimeChangeAuthorization":[]}'
  if [[ "${1:-normal}" == "t10-invalid-contract-evidence" ]]; then
    t10_red_result='{"command":"fixture","exitCode":0,"failsafeTests":0,"failureKind":"contract","failedContracts":["refresh-order"],"runtimeChangeAuthorization":["refresh-order"]}'
  elif [[ "${1:-normal}" == "t10-no-auth" ]]; then
    t10_red_result='{"command":"fixture","exitCode":1,"failsafeTests":1,"failureKind":"contract","failedContracts":["refresh-order"],"runtimeChangeAuthorization":[]}'
  elif [[ "${1:-normal}" == "t10-authorized" ]]; then
    t10_red_result='{"command":"fixture","exitCode":1,"failsafeTests":1,"failureKind":"contract","failedContracts":["refresh-order"],"runtimeChangeAuthorization":["refresh-order"]}'
  elif [[ "${1:-normal}" == "t10-wrong-auth" ]]; then
    t10_red_result='{"command":"fixture","exitCode":1,"failsafeTests":1,"failureKind":"contract","failedContracts":["refresh-order"],"runtimeChangeAuthorization":["rollback"]}'
  fi

  {
    printf '%s\n' '# Fixture plan'
    printf '%s\n' "**Baseline SHA:** $baseline_sha"
    printf '%s\n' "**Implementation Head SHA:** $implementation_head_sha"
    for task in $(seq 1 11); do
      printf '\n### T%s: fixture\n\n**Files:**\n\n' "$task"
      printf '%s\n' "- Create: \`task-$task.txt\`"
      if [[ "$task" == "1" ]]; then
        printf '%s\n' '- Test (read-only baseline): `readonly.txt`'
      fi
      if [[ "$task" == "2" ]]; then
        printf '%s\n' '- Create: `task-2-review.txt`'
      fi
      if [[ "$task" == "10" ]]; then
        printf '%s\n' '- Modify only if authorized (`refresh-order`): `t10-refresh-order.txt`'
        printf '%s\n' '- Modify only if authorized (`rollback` or `log-safety`): `t10-rollback.txt`'
      fi
      printf '\n**Execution:**\n\n'
      printf '%s\n' "- **Commit SHA:** ${task_shas[$task]}"
      if [[ "$task" == "2" ]]; then
        printf '%s\n' "- **Supplemental Commit SHAs:** [\"$supplemental_sha\"]"
      fi
      if [[ "$task" == "10" ]]; then
        printf '%s\n' "- **Red Result:** $t10_red_result"
      fi
    done
    printf '\n### T12: fixture\n\n**Files:**\n\n'
    printf '%s\n' '- Modify: `plan.md`'
    printf '%s\n' '- Create: `docs/t12-status.md`'
    printf '%s\n' '- Create: `scripts/t12-fixture.sh`'
    printf '%s\n' '' '**Execution:**' '' '- **Commit SHA:** final-record-exception'
  } >plan.md
}

initialize_fixture_seed() {
  [[ -z "$fixture_seed" ]] || return 0
  fixture_seed="$(mktemp -d -t mimir-task-commits-seed.XXXXXX)"
  git -C "$fixture_seed" init -q
  git -C "$fixture_seed" config user.name fixture
  git -C "$fixture_seed" config user.email fixture@example.invalid
  git -C "$fixture_seed" commit --allow-empty -qm 'out of range'
  out_of_range_sha="$(git -C "$fixture_seed" rev-parse HEAD)"
  printf 'baseline\n' >"$fixture_seed/readonly.txt"
  git -C "$fixture_seed" add readonly.txt
  git -C "$fixture_seed" commit -qm baseline
  baseline_sha="$(git -C "$fixture_seed" rev-parse HEAD)"
  task_shas=()

  for task in $(seq 1 11); do
    printf 'task %s\n' "$task" >"$fixture_seed/task-$task.txt"
    git -C "$fixture_seed" add "task-$task.txt"
    git -C "$fixture_seed" commit -qm "task $task"
    task_shas[$task]="$(git -C "$fixture_seed" rev-parse HEAD)"
    if [[ "$task" == "2" ]]; then
      printf 'review\n' >"$fixture_seed/task-2-review.txt"
      git -C "$fixture_seed" add task-2-review.txt
      git -C "$fixture_seed" commit -qm 'task 2 review'
      supplemental_sha="$(git -C "$fixture_seed" rev-parse HEAD)"
    fi
  done
  implementation_head_sha="$(git -C "$fixture_seed" rev-parse HEAD)"
}

create_fixture() {
  local mode="${1:-normal}"

  initialize_fixture_seed
  cleanup
  fixture_dir="$(mktemp -d -t mimir-task-commits.XXXXXX)"
  git clone -q "$fixture_seed" "$fixture_dir"
  (cd "$fixture_dir" && write_plan "$mode")
}

run_precommit_success() {
  (cd "$fixture_dir" && git add plan.md && bash "$verifier" --allow-t12-index plan.md)
}

make_t10_change_conditional() {
  sed -i '0,/- Create: `task-10.txt`/s//- Modify only if authorized (`refresh-order`): `task-10.txt`/' "$fixture_dir/plan.md"
}

test_precommit_and_final_commit() {
  create_fixture
  run_precommit_success
  mapfile -t printed_t12_paths < <(cd "$fixture_dir" && bash "$verifier" --print-t12-files0 plan.md | tr '\0' '\n')
  [[ "${printed_t12_paths[*]}" == 'plan.md docs/t12-status.md scripts/t12-fixture.sh' ]] \
    || { echo "--print-t12-files0 输出不精确: ${printed_t12_paths[*]}" >&2; return 1; }
  (cd "$fixture_dir" && git commit -qm 't12 final' && bash "$verifier" plan.md)
}

test_missing_and_duplicate_sha() {
  create_fixture
  sed -i 's/\*\*Commit SHA:\*\* [0-9a-f]\{40\}/**Commit SHA:** missing/' "$fixture_dir/plan.md"
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"

  create_fixture
  sed -i "0,/\*\*Commit SHA:\*\* ${task_shas[2]}/s//**Commit SHA:** ${task_shas[1]}/" "$fixture_dir/plan.md"
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
}

test_out_of_range_declared_sha() {
  create_fixture
  sed -i "0,/\*\*Commit SHA:\*\* ${task_shas[3]}/s//**Commit SHA:** $out_of_range_sha/" "$fixture_dir/plan.md"
  sed -i "/\*\*Commit SHA:\*\* $out_of_range_sha/a- **Supplemental Commit SHAs:** [\"${task_shas[3]}\"]" "$fixture_dir/plan.md"
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
}

test_extra_and_scope_violations() {
  create_fixture
  run_precommit_success
  (cd "$fixture_dir" && git commit -qm 't12 final' && printf 'extra\n' >extra.txt && git add extra.txt && git commit -qm extra)
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' plan.md"

  create_fixture
  sed -i '0,/- Create: `task-3.txt`/s//- Create: `wrong-task-3.txt`/' "$fixture_dir/plan.md"
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
}

test_readonly_and_t10_authorization() {
  create_fixture
  sed -i '/- Test (read-only baseline): `readonly.txt`/a- Test (read-only baseline): `task-1.txt`' "$fixture_dir/plan.md"
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"

  create_fixture t10-no-auth
  make_t10_change_conditional
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"

  create_fixture t10-authorized
  make_t10_change_conditional
  run_precommit_success

  create_fixture t10-invalid-contract-evidence
  make_t10_change_conditional
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"

  create_fixture t10-wrong-auth
  make_t10_change_conditional
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
}

test_index_and_worktree_violations() {
  create_fixture
  (cd "$fixture_dir" && git add plan.md && mkdir -p docs && touch docs/t12-status.md && git add docs/t12-status.md && bash "$verifier" --allow-t12-index plan.md)
  (cd "$fixture_dir" && touch outside-index.txt && git add outside-index.txt)
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
  (cd "$fixture_dir" && git reset -q outside-index.txt && rm outside-index.txt && touch outside-worktree.txt)
  expect_failure bash -c "cd '$fixture_dir' && bash '$verifier' --allow-t12-index plan.md"
}

test_precommit_and_final_commit
test_missing_and_duplicate_sha
test_out_of_range_declared_sha
test_extra_and_scope_violations
test_readonly_and_t10_authorization
test_index_and_worktree_violations
echo 'verify-task-commits fixture tests passed'
