@echo off
chcp 65001 >nul
setlocal

set DEVELOP_BRANCH=develop
set MASTER_BRANCH=master
set VERSION=%1

if "%VERSION%"=="-help" goto help
if "%VERSION%"=="-h" goto help
if "%VERSION%"=="--help" goto help
if "%VERSION%"=="" goto help_error

set TAG_NAME=v%VERSION%
set RELEASE_BRANCH=release/v%VERSION%

git rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
    echo 当前目录不是 Git 仓库
    exit /b 1
)

for /f %%i in ('git status --porcelain') do (
    echo 当前工作区存在未提交的修改，请先提交或暂存
    git status --short
    exit /b 1
)

echo 拉取远程分支信息...
git fetch origin
if errorlevel 1 (
    echo git fetch 失败
    exit /b 1
)

git show-ref --verify --quiet "refs/heads/%RELEASE_BRANCH%"
if not errorlevel 1 (
    echo 本地 release 分支已存在: %RELEASE_BRANCH%
    exit /b 1
)

git ls-remote --exit-code --heads origin "%RELEASE_BRANCH%" >nul 2>nul
if not errorlevel 1 (
    echo 远程 release 分支已存在: %RELEASE_BRANCH%
    exit /b 1
)

git show-ref --verify --quiet "refs/tags/%TAG_NAME%"
if not errorlevel 1 (
    echo 本地 Tag 已存在: %TAG_NAME%
    exit /b 1
)

git ls-remote --exit-code --tags origin "refs/tags/%TAG_NAME%" >nul 2>nul
if not errorlevel 1 (
    echo 远程 Tag 已存在: %TAG_NAME%
    exit /b 1
)

echo 切换到 %MASTER_BRANCH% 分支...
git checkout %MASTER_BRANCH%
if errorlevel 1 (
    echo 切换 %MASTER_BRANCH% 失败
    exit /b 1
)

echo 拉取 %MASTER_BRANCH% 最新代码...
git pull origin %MASTER_BRANCH%
if errorlevel 1 (
    echo 拉取 %MASTER_BRANCH% 失败
    exit /b 1
)

echo 将 %DEVELOP_BRANCH% 合并到 %MASTER_BRANCH%...

git merge --no-ff origin/%DEVELOP_BRANCH% -m "Merge %DEVELOP_BRANCH% into %MASTER_BRANCH% for %VERSION%"
if errorlevel 1 (
    echo.
    echo 合并失败：%DEVELOP_BRANCH% 合并到 %MASTER_BRANCH% 时发生冲突
    echo 已取消本次 merge，请手动处理冲突后重新执行脚本

    git merge --abort >nul 2>nul

    exit /b 1
)

echo 合并成功

echo 推送 %MASTER_BRANCH% 到远程...
git push origin %MASTER_BRANCH%
if errorlevel 1 (
    echo 推送 %MASTER_BRANCH% 失败
    exit /b 1
)

echo 在 %MASTER_BRANCH% 上创建 Tag: %TAG_NAME%
git tag -a %TAG_NAME% -m "Release %VERSION%"
if errorlevel 1 (
    echo 创建 Tag 失败
    exit /b 1
)

echo 推送 Tag 到远程...
git push origin %TAG_NAME%
if errorlevel 1 (
    echo 推送 Tag 失败
    exit /b 1
)

echo 从 %MASTER_BRANCH% 创建 release 分支: %RELEASE_BRANCH%
git checkout -b %RELEASE_BRANCH%
if errorlevel 1 (
    echo 创建 release 分支失败
    exit /b 1
)

echo 推送 release 分支到远程...
git push -u origin %RELEASE_BRANCH%
if errorlevel 1 (
    echo 推送 release 分支失败
    exit /b 1
)

echo.
echo 发布分支创建完成
echo Master 分支: %MASTER_BRANCH%
echo Tag: %TAG_NAME%
echo Release 分支: %RELEASE_BRANCH%

exit /b 0

:help
echo 用法:
echo   release.bat ^<版本号^>
echo.
echo 参数:
echo   ^<版本号^>    发布版本号，例如 1.0.0
echo.
echo 示例:
echo   release.bat 1.0.0
echo.
echo 执行流程:
echo   1. 检查当前目录是否为 Git 仓库
echo   2. 检查工作区是否干净
echo   3. 拉取远程分支信息
echo   4. 切换到 master 并拉取最新代码
echo   5. 将 develop 合并到 master
echo   6. 如果有冲突，终止并回滚 merge
echo   7. 推送 master
echo   8. 在 master 上创建 tag
echo   9. 从 master 创建 release/^<版本号^> 分支
echo   10. 推送 release 分支
exit /b 0

:help_error
call :help
exit /b 1