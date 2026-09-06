use anyhow::{anyhow, bail, Context, Result};
use git2::{
    Delta, Diff, DiffDelta, DiffFindOptions, DiffOptions, Oid, Patch, Repository, Status,
    StatusOptions, Tree,
};
use jni::{
    objects::{JObject, JString},
    sys::{jint, jstring},
    JNIEnv,
};
use serde::Serialize;
use std::{
    fs,
    panic::{catch_unwind, AssertUnwindSafe},
    path::{Path, PathBuf},
    ptr,
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};

const SHARED_STORAGE_ROOT: &str = "/storage/emulated/0";
const COMMIT_HISTORY_PAGE_SIZE: usize = 20;
const MAXIMUM_UNTRACKED_FILE_BYTES: u64 = 2 * 1024 * 1024;
const BINARY_DETECTION_BYTES: usize = 8_000;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct RepositoryDiffData {
    repository: String,
    branch: String,
    latest_commit: CommitDiffData,
    commit_history: CommitHistoryPageData,
    sections: Vec<DiffSectionData>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CommitDiffData {
    id: String,
    subject: String,
    author_name: String,
    authored_at: String,
    files: Vec<FileDiffData>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CommitHistoryPageData {
    commits: Vec<CommitSummaryData>,
    next_offset: Option<usize>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CommitSummaryData {
    id: String,
    subject: String,
    author_name: String,
    authored_at: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DiffSectionData {
    kind: DiffSectionKindData,
    files: Vec<FileDiffData>,
}

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
enum DiffSectionKindData {
    Unstaged,
    Staged,
    Untracked,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct FileDiffData {
    old_path: Option<String>,
    new_path: Option<String>,
    status: FileDiffStatusData,
    is_binary: bool,
    content_unavailable_message: Option<String>,
    hunks: Vec<DiffHunkData>,
}

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
enum FileDiffStatusData {
    Modified,
    Added,
    Deleted,
    Renamed,
    Untracked,
}

#[derive(Serialize)]
struct DiffHunkData {
    header: String,
    lines: Vec<DiffLineData>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DiffLineData {
    kind: DiffLineKindData,
    content: String,
    old_line: Option<u32>,
    new_line: Option<u32>,
}

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
enum DiffLineKindData {
    Context,
    Addition,
    Deletion,
    Meta,
}

#[no_mangle]
pub extern "system" fn Java_com_example_diffviewer_core_data_NativeGitBridge_fetchRepositoryDiff(
    mut environment: JNIEnv,
    _receiver: JObject,
    repository_path: JString,
) -> jstring {
    execute_json_call(&mut environment, |environment| {
        let repository_path = read_java_string(environment, &repository_path)?;
        fetch_repository_diff_json(&repository_path, Path::new(SHARED_STORAGE_ROOT))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_example_diffviewer_core_data_NativeGitBridge_fetchCommitHistoryPage(
    mut environment: JNIEnv,
    _receiver: JObject,
    repository_path: JString,
    offset: jint,
) -> jstring {
    execute_json_call(&mut environment, |environment| {
        let repository_path = read_java_string(environment, &repository_path)?;
        let offset = usize::try_from(offset).context("履歴の読み込み位置が不正です")?;
        fetch_commit_history_page_json(&repository_path, Path::new(SHARED_STORAGE_ROOT), offset)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_example_diffviewer_core_data_NativeGitBridge_fetchCommitDiff(
    mut environment: JNIEnv,
    _receiver: JObject,
    repository_path: JString,
    commit_id: JString,
) -> jstring {
    execute_json_call(&mut environment, |environment| {
        let repository_path = read_java_string(environment, &repository_path)?;
        let commit_id = read_java_string(environment, &commit_id)?;
        fetch_commit_diff_json(&repository_path, Path::new(SHARED_STORAGE_ROOT), &commit_id)
    })
}

fn execute_json_call(
    environment: &mut JNIEnv,
    operation: impl FnOnce(&mut JNIEnv) -> Result<String>,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| operation(environment)));
    match result {
        Ok(Ok(json)) => match environment.new_string(json) {
            Ok(java_string) => java_string.into_raw(),
            Err(error) => {
                throw_io_exception(environment, &error.to_string());
                ptr::null_mut()
            }
        },
        Ok(Err(error)) => {
            throw_io_exception(environment, &format!("{error:#}"));
            ptr::null_mut()
        }
        Err(_) => {
            throw_io_exception(environment, "端末内Git処理で予期しないエラーが発生しました");
            ptr::null_mut()
        }
    }
}

fn read_java_string(environment: &mut JNIEnv, value: &JString) -> Result<String> {
    Ok(environment.get_string(value)?.into())
}

fn throw_io_exception(environment: &mut JNIEnv, message: &str) {
    let _ = environment.throw_new("java/io/IOException", message);
}

fn fetch_repository_diff_json(repository_path: &str, allowed_root: &Path) -> Result<String> {
    let (repository, work_tree) = open_selected_repository(repository_path, allowed_root)?;
    let latest_commit_oid = find_latest_commit_oid(&repository)?;
    let untracked_paths = find_untracked_paths(&repository)?;
    let repository_diff_data = RepositoryDiffData {
        repository: path_text(&work_tree),
        branch: find_branch_name(&repository)?,
        latest_commit: create_commit_diff(&repository, latest_commit_oid)?,
        commit_history: create_commit_history_page(&repository, 0)?,
        sections: vec![
            DiffSectionData {
                kind: DiffSectionKindData::Unstaged,
                files: create_unstaged_diffs(&repository)?,
            },
            DiffSectionData {
                kind: DiffSectionKindData::Staged,
                files: create_staged_diffs(&repository)?,
            },
            DiffSectionData {
                kind: DiffSectionKindData::Untracked,
                files: create_untracked_diffs(&work_tree, &untracked_paths)?,
            },
        ],
    };
    serde_json::to_string(&repository_diff_data).context("Git差分JSONを作成できません")
}

fn fetch_commit_history_page_json(
    repository_path: &str,
    allowed_root: &Path,
    offset: usize,
) -> Result<String> {
    let (repository, _) = open_selected_repository(repository_path, allowed_root)?;
    let commit_history_page_data = create_commit_history_page(&repository, offset)?;
    serde_json::to_string(&commit_history_page_data).context("コミット履歴JSONを作成できません")
}

fn fetch_commit_diff_json(
    repository_path: &str,
    allowed_root: &Path,
    commit_id: &str,
) -> Result<String> {
    let (repository, _) = open_selected_repository(repository_path, allowed_root)?;
    let commit_oid = parse_commit_oid(commit_id)?;
    let commit_diff_data = create_commit_diff(&repository, commit_oid)?;
    serde_json::to_string(&commit_diff_data).context("コミット差分JSONを作成できません")
}

fn open_selected_repository(
    repository_path: &str,
    allowed_root: &Path,
) -> Result<(Repository, PathBuf)> {
    let selected_directory = canonical_selected_directory(repository_path, allowed_root)?;
    let repository = Repository::open(&selected_directory)
        .context("選択したフォルダ直下にGit作業ツリーがありません")?;
    if repository.is_bare() {
        bail!("選択したフォルダ直下にGit作業ツリーがありません");
    }
    let work_tree = repository
        .workdir()
        .context("選択したフォルダ直下にGit作業ツリーがありません")?
        .canonicalize()
        .context("Git作業ツリーの場所を確認できません")?;
    if work_tree != selected_directory {
        bail!("選択したフォルダ直下にGit作業ツリーがありません");
    }
    Ok((repository, work_tree))
}

fn canonical_selected_directory(repository_path: &str, allowed_root: &Path) -> Result<PathBuf> {
    let selected_directory = Path::new(repository_path)
        .canonicalize()
        .context("共有ストレージ内のフォルダを選択してください")?;
    let allowed_root = allowed_root
        .canonicalize()
        .context("共有ストレージを読み取れません")?;
    if !selected_directory.is_dir() || !selected_directory.starts_with(&allowed_root) {
        bail!("共有ストレージ内のフォルダを選択してください");
    }
    Ok(selected_directory)
}

fn find_latest_commit_oid(repository: &Repository) -> Result<Oid> {
    repository
        .head()
        .and_then(|head| head.peel_to_commit())
        .map(|commit| commit.id())
        .context("このリポジトリにはコミットがありません")
}

fn find_branch_name(repository: &Repository) -> Result<String> {
    let head = repository.head().context("HEADを読み取れません")?;
    if head.is_branch() {
        Ok(head.shorthand().unwrap_or("HEAD").to_owned())
    } else {
        Ok("detached HEAD".to_owned())
    }
}

fn find_untracked_paths(repository: &Repository) -> Result<Vec<String>> {
    let mut options = StatusOptions::new();
    options
        .include_untracked(true)
        .recurse_untracked_dirs(true)
        .include_ignored(false);
    let statuses = repository.statuses(Some(&mut options))?;
    let mut paths = statuses
        .iter()
        .filter(|entry| entry.status().contains(Status::WT_NEW))
        .map(|entry| entry.path().map(str::to_owned))
        .collect::<std::result::Result<Vec<_>, _>>()?;
    paths.sort();
    Ok(paths)
}

fn create_unstaged_diffs(repository: &Repository) -> Result<Vec<FileDiffData>> {
    let mut options = standard_diff_options();
    options.include_untracked(false);
    let mut diff = repository.diff_index_to_workdir(None, Some(&mut options))?;
    create_file_diffs(&mut diff)
}

fn create_staged_diffs(repository: &Repository) -> Result<Vec<FileDiffData>> {
    let head_tree = find_head_tree(repository)?;
    let mut options = standard_diff_options();
    let mut diff = repository.diff_tree_to_index(head_tree.as_ref(), None, Some(&mut options))?;
    create_file_diffs(&mut diff)
}

fn find_head_tree(repository: &Repository) -> Result<Option<Tree<'_>>> {
    match repository.head() {
        Ok(head) => Ok(Some(head.peel_to_tree()?)),
        Err(error) if error.code() == git2::ErrorCode::UnbornBranch => Ok(None),
        Err(error) => Err(error.into()),
    }
}

fn standard_diff_options() -> DiffOptions {
    let mut options = DiffOptions::new();
    options
        .context_lines(3)
        .include_typechange(true)
        .include_typechange_trees(true)
        .recurse_untracked_dirs(true);
    options
}

fn create_file_diffs(diff: &mut Diff) -> Result<Vec<FileDiffData>> {
    let mut find_options = DiffFindOptions::new();
    find_options.renames(true);
    diff.find_similar(Some(&mut find_options))?;
    (0..diff.deltas().len())
        .map(|delta_index| create_file_diff(diff, delta_index))
        .collect()
}

fn create_file_diff(diff: &Diff, delta_index: usize) -> Result<FileDiffData> {
    let delta = diff
        .get_delta(delta_index)
        .context("変更ファイル情報を読み取れません")?;
    let patch = Patch::from_diff(diff, delta_index)?;
    let is_binary = delta.old_file().is_binary() || delta.new_file().is_binary();
    let (old_path, new_path) = normalized_diff_paths(&delta);
    let hunks = match patch.as_ref() {
        Some(patch) if !is_binary => create_diff_hunks(patch)?,
        _ => Vec::new(),
    };
    Ok(FileDiffData {
        old_path,
        new_path,
        status: file_diff_status(&delta)?,
        is_binary,
        content_unavailable_message: None,
        hunks,
    })
}

fn create_diff_hunks(patch: &Patch) -> Result<Vec<DiffHunkData>> {
    (0..patch.num_hunks())
        .map(|hunk_index| {
            let (hunk, line_count) = patch.hunk(hunk_index)?;
            let lines = (0..line_count)
                .map(|line_index| {
                    let line = patch.line_in_hunk(hunk_index, line_index)?;
                    Ok(DiffLineData {
                        kind: diff_line_kind(line.origin()),
                        content: line_content(line.content()),
                        old_line: line.old_lineno(),
                        new_line: line.new_lineno(),
                    })
                })
                .collect::<Result<Vec<_>>>()?;
            Ok(DiffHunkData {
                header: line_content(hunk.header()),
                lines,
            })
        })
        .collect()
}

fn diff_line_kind(origin: char) -> DiffLineKindData {
    match origin {
        '+' => DiffLineKindData::Addition,
        '-' => DiffLineKindData::Deletion,
        ' ' => DiffLineKindData::Context,
        _ => DiffLineKindData::Meta,
    }
}

fn line_content(bytes: &[u8]) -> String {
    String::from_utf8_lossy(bytes)
        .trim_end_matches(['\r', '\n'])
        .to_owned()
}

fn diff_path(path: Option<&Path>) -> Option<String> {
    path.map(path_text)
}

fn path_text(path: &Path) -> String {
    path.to_string_lossy().into_owned()
}

fn normalized_diff_paths(delta: &DiffDelta) -> (Option<String>, Option<String>) {
    match delta.status() {
        Delta::Added | Delta::Copied => (None, diff_path(delta.new_file().path())),
        Delta::Deleted => (diff_path(delta.old_file().path()), None),
        _ => (
            diff_path(delta.old_file().path()),
            diff_path(delta.new_file().path()),
        ),
    }
}

fn file_diff_status(delta: &DiffDelta) -> Result<FileDiffStatusData> {
    match delta.status() {
        Delta::Added | Delta::Copied => Ok(FileDiffStatusData::Added),
        Delta::Deleted => Ok(FileDiffStatusData::Deleted),
        Delta::Modified | Delta::Typechange => Ok(FileDiffStatusData::Modified),
        Delta::Renamed => Ok(FileDiffStatusData::Renamed),
        status => Err(anyhow!("未対応のGit差分状態です: {status:?}")),
    }
}

fn create_untracked_diffs(work_tree: &Path, paths: &[String]) -> Result<Vec<FileDiffData>> {
    paths
        .iter()
        .map(|path| create_untracked_file_diff(work_tree, path))
        .collect()
}

fn create_untracked_file_diff(work_tree: &Path, path: &str) -> Result<FileDiffData> {
    let file = resolve_work_tree_file(work_tree, path)?;
    let metadata = file.metadata()?;
    if metadata.len() > MAXIMUM_UNTRACKED_FILE_BYTES {
        return Ok(FileDiffData {
            old_path: None,
            new_path: Some(path.to_owned()),
            status: FileDiffStatusData::Untracked,
            is_binary: false,
            content_unavailable_message: Some(
                "ファイルが大きすぎるため内容を表示できません".to_owned(),
            ),
            hunks: Vec::new(),
        });
    }
    let content = fs::read(file)?;
    let is_binary = content
        .iter()
        .take(BINARY_DETECTION_BYTES)
        .any(|byte| *byte == 0);
    Ok(FileDiffData {
        old_path: None,
        new_path: Some(path.to_owned()),
        status: FileDiffStatusData::Untracked,
        is_binary,
        content_unavailable_message: None,
        hunks: if is_binary {
            Vec::new()
        } else {
            create_added_file_hunks(&content)
        },
    })
}

fn resolve_work_tree_file(work_tree: &Path, path: &str) -> Result<PathBuf> {
    let file = work_tree
        .join(path)
        .canonicalize()
        .with_context(|| format!("未追跡ファイルを読み取れません: {path}"))?;
    if !file.starts_with(work_tree) || !file.is_file() {
        bail!("未追跡ファイルを読み取れません: {path}");
    }
    Ok(file)
}

fn create_added_file_hunks(content: &[u8]) -> Vec<DiffHunkData> {
    if content.is_empty() {
        return Vec::new();
    }
    let content = String::from_utf8_lossy(content);
    let lines = content
        .split_terminator('\n')
        .enumerate()
        .map(|(index, line)| DiffLineData {
            kind: DiffLineKindData::Addition,
            content: line.trim_end_matches('\r').to_owned(),
            old_line: None,
            new_line: Some((index + 1) as u32),
        })
        .collect::<Vec<_>>();
    if lines.is_empty() {
        return Vec::new();
    }
    vec![DiffHunkData {
        header: format!("@@ -0,0 +1,{} @@", lines.len()),
        lines,
    }]
}

fn create_commit_history_page(
    repository: &Repository,
    offset: usize,
) -> Result<CommitHistoryPageData> {
    let commit_oids =
        collect_first_parent_commits(repository, offset, COMMIT_HISTORY_PAGE_SIZE + 1)?;
    let commits = commit_oids
        .iter()
        .take(COMMIT_HISTORY_PAGE_SIZE)
        .map(|oid| create_commit_summary(repository, *oid))
        .collect::<Result<Vec<_>>>()?;
    Ok(CommitHistoryPageData {
        commits,
        next_offset: (commit_oids.len() > COMMIT_HISTORY_PAGE_SIZE)
            .then_some(offset + COMMIT_HISTORY_PAGE_SIZE),
    })
}

fn collect_first_parent_commits(
    repository: &Repository,
    offset: usize,
    limit: usize,
) -> Result<Vec<Oid>> {
    let mut current_oid = Some(find_latest_commit_oid(repository)?);
    let mut current_offset = 0;
    let mut commit_oids = Vec::new();
    while let Some(oid) = current_oid {
        let commit = repository.find_commit(oid)?;
        if current_offset >= offset {
            commit_oids.push(oid);
            if commit_oids.len() == limit {
                break;
            }
        }
        current_offset += 1;
        current_oid = (commit.parent_count() > 0)
            .then(|| commit.parent_id(0))
            .transpose()?;
    }
    Ok(commit_oids)
}

fn create_commit_summary(repository: &Repository, oid: Oid) -> Result<CommitSummaryData> {
    let commit = repository.find_commit(oid)?;
    let subject = commit.summary()?.unwrap_or("").to_owned();
    let author = commit.author();
    let author_name = author.name().unwrap_or("").to_owned();
    Ok(CommitSummaryData {
        id: commit.id().to_string(),
        subject,
        author_name,
        authored_at: format_git_time(commit.time().seconds())?,
    })
}

fn create_commit_diff(repository: &Repository, oid: Oid) -> Result<CommitDiffData> {
    let commit = repository.find_commit(oid)?;
    let subject = commit.summary()?.unwrap_or("").to_owned();
    let author = commit.author();
    let author_name = author.name().unwrap_or("").to_owned();
    let current_tree = commit.tree()?;
    let parent_tree = if commit.parent_count() == 0 {
        None
    } else {
        Some(commit.parent(0)?.tree()?)
    };
    let mut options = standard_diff_options();
    let mut diff = repository.diff_tree_to_tree(
        parent_tree.as_ref(),
        Some(&current_tree),
        Some(&mut options),
    )?;
    Ok(CommitDiffData {
        id: commit.id().to_string(),
        subject,
        author_name,
        authored_at: format_git_time(commit.time().seconds())?,
        files: create_file_diffs(&mut diff)?,
    })
}

fn parse_commit_oid(commit_id: &str) -> Result<Oid> {
    if commit_id.len() != 40 || !commit_id.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        bail!("コミットIDが不正です");
    }
    Oid::from_str(commit_id).context("コミットIDが不正です")
}

fn format_git_time(seconds: i64) -> Result<String> {
    OffsetDateTime::from_unix_timestamp(seconds)?
        .format(&Rfc3339)
        .context("コミット日時を変換できません")
}

#[cfg(test)]
mod tests {
    use super::*;
    use git2::{IndexAddOption, Signature};
    use serde_json::Value;
    use std::io::Write;
    use tempfile::TempDir;

    #[test]
    fn repository_json_separates_staged_unstaged_and_untracked_changes() {
        let fixture = RepositoryFixture::new();
        fixture.write("tracked.txt", "base\n");
        fixture.commit_all("initial");
        fixture.write("tracked.txt", "staged\n");
        fixture.stage("tracked.txt");
        fixture.write("tracked.txt", "staged\nunstaged\n");
        fixture.write("untracked.txt", "new\n");

        let json = fetch_repository_diff_json(fixture.path_text(), fixture.root()).unwrap();
        let value: Value = serde_json::from_str(&json).unwrap();

        assert_eq!(
            value["repository"],
            fixture.path_text().trim_end_matches('/')
        );
        assert!(value["latestCommit"]["files"].is_array());
        assert_eq!(
            value["commitHistory"]["commits"].as_array().unwrap().len(),
            1
        );
        assert_eq!(section_files(&value, "unstaged").len(), 1);
        assert_eq!(section_files(&value, "staged").len(), 1);
        assert_eq!(section_files(&value, "untracked").len(), 1);
        assert_eq!(section_files(&value, "untracked")[0]["status"], "untracked");
    }

    #[test]
    fn initial_commit_is_diffed_against_an_empty_tree() {
        let fixture = RepositoryFixture::new();
        fixture.write("first.txt", "first\n");
        let commit_id = fixture.commit_all("initial");

        let json = fetch_commit_diff_json(fixture.path_text(), fixture.root(), &commit_id).unwrap();
        let value: Value = serde_json::from_str(&json).unwrap();

        assert_eq!(value["files"][0]["status"], "added");
        assert_eq!(
            value["files"][0]["hunks"][0]["lines"][0]["kind"],
            "addition"
        );
    }

    #[test]
    fn detached_head_is_reported() {
        let fixture = RepositoryFixture::new();
        fixture.write("tracked.txt", "base\n");
        let commit_id = fixture.commit_all("initial");
        fixture
            .repository
            .set_head_detached(Oid::from_str(&commit_id).unwrap())
            .unwrap();

        let json = fetch_repository_diff_json(fixture.path_text(), fixture.root()).unwrap();
        let value: Value = serde_json::from_str(&json).unwrap();
        assert_eq!(value["branch"], "detached HEAD");
    }

    #[test]
    fn commit_history_uses_twenty_item_pages() {
        let fixture = RepositoryFixture::new();
        for index in 0..21 {
            fixture.write("history.txt", &format!("{index}\n"));
            fixture.commit_all(&format!("commit {index}"));
        }

        let first_json =
            fetch_commit_history_page_json(fixture.path_text(), fixture.root(), 0).unwrap();
        let first: Value = serde_json::from_str(&first_json).unwrap();
        let second_json =
            fetch_commit_history_page_json(fixture.path_text(), fixture.root(), 20).unwrap();
        let second: Value = serde_json::from_str(&second_json).unwrap();

        assert_eq!(first["commits"].as_array().unwrap().len(), 20);
        assert_eq!(first["nextOffset"], 20);
        assert_eq!(second["commits"].as_array().unwrap().len(), 1);
        assert!(second["nextOffset"].is_null());
    }

    #[test]
    fn oversized_and_binary_untracked_files_remain_visible_without_hunks() {
        let fixture = RepositoryFixture::new();
        fixture.write("tracked.txt", "base\n");
        fixture.commit_all("initial");
        fixture.write_bytes("binary.bin", &[0, 1, 2]);
        let mut oversized = fs::File::create(fixture.path().join("oversized.txt")).unwrap();
        oversized.set_len(MAXIMUM_UNTRACKED_FILE_BYTES + 1).unwrap();
        oversized.flush().unwrap();

        let json = fetch_repository_diff_json(fixture.path_text(), fixture.root()).unwrap();
        let value: Value = serde_json::from_str(&json).unwrap();
        let files = section_files(&value, "untracked");
        let binary = files
            .iter()
            .find(|file| file["newPath"] == "binary.bin")
            .unwrap();
        let oversized = files
            .iter()
            .find(|file| file["newPath"] == "oversized.txt")
            .unwrap();

        assert_eq!(binary["isBinary"], true);
        assert!(binary["hunks"].as_array().unwrap().is_empty());
        assert!(oversized["contentUnavailableMessage"].is_string());
        assert!(oversized["hunks"].as_array().unwrap().is_empty());
    }

    #[test]
    fn tracked_binary_commit_is_reported_without_hunks() {
        let fixture = RepositoryFixture::new();
        fixture.write_bytes("binary.bin", &[0, 1, 2]);
        let commit_id = fixture.commit_all("binary");

        let json = fetch_commit_diff_json(fixture.path_text(), fixture.root(), &commit_id).unwrap();
        let value: Value = serde_json::from_str(&json).unwrap();

        assert_eq!(value["files"][0]["isBinary"], true);
        assert!(value["files"][0]["hunks"].as_array().unwrap().is_empty());
    }

    #[test]
    fn repository_outside_allowed_root_is_rejected() {
        let fixture = RepositoryFixture::new();
        fixture.write("tracked.txt", "base\n");
        fixture.commit_all("initial");
        let different_root = tempfile::tempdir().unwrap();

        let result = fetch_repository_diff_json(fixture.path_text(), different_root.path());
        assert!(result.is_err());
    }
    fn section_files<'a>(value: &'a Value, kind: &str) -> &'a Vec<Value> {
        value["sections"]
            .as_array()
            .unwrap()
            .iter()
            .find(|section| section["kind"] == kind)
            .unwrap()["files"]
            .as_array()
            .unwrap()
    }

    struct RepositoryFixture {
        root: TempDir,
        repository: Repository,
    }

    impl RepositoryFixture {
        fn new() -> Self {
            let root = tempfile::tempdir().unwrap();
            let repository_path = root.path().join("repository");
            fs::create_dir(&repository_path).unwrap();
            let repository = Repository::init(&repository_path).unwrap();
            Self { root, repository }
        }

        fn root(&self) -> &Path {
            self.root.path()
        }

        fn path(&self) -> &Path {
            self.repository.workdir().unwrap()
        }

        fn path_text(&self) -> &str {
            self.path().to_str().unwrap()
        }

        fn write(&self, path: &str, content: &str) {
            self.write_bytes(path, content.as_bytes());
        }

        fn write_bytes(&self, path: &str, content: &[u8]) {
            fs::write(self.path().join(path), content).unwrap();
        }

        fn stage(&self, path: &str) {
            let mut index = self.repository.index().unwrap();
            index.add_path(Path::new(path)).unwrap();
            index.write().unwrap();
        }

        fn commit_all(&self, message: &str) -> String {
            let mut index = self.repository.index().unwrap();
            index
                .add_all(["*"].iter(), IndexAddOption::DEFAULT, None)
                .unwrap();
            index.write().unwrap();
            let tree_oid = index.write_tree().unwrap();
            let tree = self.repository.find_tree(tree_oid).unwrap();
            let signature = Signature::now("Test", "test@example.com").unwrap();
            let parent = self
                .repository
                .head()
                .ok()
                .and_then(|head| head.target())
                .map(|oid| self.repository.find_commit(oid).unwrap());
            let parents = parent.iter().collect::<Vec<_>>();
            self.repository
                .commit(
                    Some("HEAD"),
                    &signature,
                    &signature,
                    message,
                    &tree,
                    &parents,
                )
                .unwrap()
                .to_string()
        }
    }
}
