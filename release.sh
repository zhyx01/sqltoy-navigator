#!/usr/bin/env bash

set -euo pipefail

DEVELOP_BRANCH="develop"
MASTER_BRANCH="master"

show_help() {
  echo "用法:"
  echo "  sh release.sh <版本号>"
  echo ""
  echo "参数:"
  echo "  <版本号>    发布版本号，例如 1.0.0"
  echo ""
  echo "示例:"
  echo "  sh release.sh 1.0.0"
  echo ""
  echo "执行流程:"
  echo "  1. 检查当前目录是否为 Git 仓库"
  echo "  2. 检查工作区是否干净"
  echo "  3. 拉取远程分支信息"
  echo "  4. 切换到 master 并拉取最新代码"
  echo "  5. 将 develop 合并到 master"
  echo "  6. 如果有冲突，终止并回滚 merge"
  echo "  7. 推送 master"
  echo "  8. 在 master 上创建 tag"
  echo "  9. 从 master 创建 release/<版本号> 分支"
  echo "  10. 推送 release 分支"
}

VERSION="${1:-}"

if [ "$VERSION" = "-help" ] || [ "$VERSION" = "-h" ] || [ "$VERSION" = "--help" ]; then
  show_help
  exit 0
fi

if [ -z "$VERSION" ]; then
  show_help
  exit 1
fi

TAG_NAME="v$VERSION"
RELEASE_BRANCH="release/v$VERSION"

if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  echo "当前目录不是 Git 仓库"
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "当前工作区存在未提交的修改，请先提交或暂存"
  git status --short
  exit 1
fi

echo "拉取远程分支信息..."
git fetch origin

if git show-ref --verify --quiet "refs/heads/$RELEASE_BRANCH"; then
  echo "本地 release 分支已存在: $RELEASE_BRANCH"
  exit 1
fi

if git ls-remote --exit-code --heads origin "$RELEASE_BRANCH" > /dev/null 2>&1; then
  echo "远程 release 分支已存在: $RELEASE_BRANCH"
  exit 1
fi

if git show-ref --verify --quiet "refs/tags/$TAG_NAME"; then
  echo "本地 Tag 已存在: $TAG_NAME"
  exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$TAG_NAME" > /dev/null 2>&1; then
  echo "远程 Tag 已存在: $TAG_NAME"
  exit 1
fi

echo "切换到 $MASTER_BRANCH 分支..."
git checkout "$MASTER_BRANCH"

echo "拉取 $MASTER_BRANCH 最新代码..."
git pull origin "$MASTER_BRANCH"

echo "将 $DEVELOP_BRANCH 合并到 $MASTER_BRANCH..."

if ! git merge --no-ff "origin/$DEVELOP_BRANCH" -m "Merge $DEVELOP_BRANCH into $MASTER_BRANCH for $VERSION"; then
  echo ""
  echo "合并失败：$DEVELOP_BRANCH 合并到 $MASTER_BRANCH 时发生冲突"
  echo "已取消本次 merge，请手动处理冲突后重新执行脚本"

  git merge --abort > /dev/null 2>&1 || true

  exit 1
fi

echo "合并成功"

echo "推送 $MASTER_BRANCH 到远程..."
git push origin "$MASTER_BRANCH"

echo "在 $MASTER_BRANCH 上创建 Tag: $TAG_NAME"
git tag -a "$TAG_NAME" -m "Release $VERSION"

echo "推送 Tag 到远程..."
git push origin "$TAG_NAME"

echo "从 $MASTER_BRANCH 创建 release 分支: $RELEASE_BRANCH"
git checkout -b "$RELEASE_BRANCH"

echo "推送 release 分支到远程..."
git push -u origin "$RELEASE_BRANCH"

echo ""
echo "发布分支创建完成"
echo "Master 分支: $MASTER_BRANCH"
echo "Tag: $TAG_NAME"
echo "Release 分支: $RELEASE_BRANCH"