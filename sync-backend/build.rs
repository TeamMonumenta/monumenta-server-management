use std::process::Command;

fn main() {
    let output = Command::new("git")
        .args(["describe", "--no-match", "--always", "--dirty"])
        .output()
        .unwrap();

    let git_hash = String::from_utf8(output.stdout).unwrap();
    println!("cargo:rustc-env=GIT_VERSION={}", git_hash);
}
