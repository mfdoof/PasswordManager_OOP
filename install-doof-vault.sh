#!/usr/bin/env bash
# DOOF Vault installer for macOS and Linux.
#
#   install-doof-vault.sh                     install or upgrade to the latest release
#   install-doof-vault.sh --version 2.0.1     install a specific release
#   install-doof-vault.sh --uninstall         remove the program (never touches ~/.doof: vault and backups)
#
# Downloads only from https://github.com/mfdoof/PasswordManager_OOP releases (redirects must stay
# HTTPS), verifies the jar against the release's SHA256SUMS and installs nothing on a mismatch.
# Installs ~/.local/share/doof/doof-vault.jar and the launcher ~/.local/bin/doofv. Needs Java 17+.
# Exit codes: 0 ok, 1 failed (nothing changed), 2 usage error.
set -euo pipefail

REPO_URL="https://github.com/mfdoof/PasswordManager_OOP"
SHARE_DIR="$HOME/.local/share/doof"
BIN_DIR="$HOME/.local/bin"
JAR="$SHARE_DIR/doof-vault.jar"
VERSION_FILE="$SHARE_DIR/VERSION"
LAUNCHER="$BIN_DIR/doofv"
LAUNCHER_MARK="# DOOF Vault launcher (installed by install-doof-vault.sh)"
# Launcher written by the earlier install.sh (command "doof"); removed when found, never otherwise.
LEGACY_LAUNCHER="$BIN_DIR/doof"
LEGACY_MARK="# DOOF Vault launcher (installed by install.sh)"
MIN_JAVA=17
VERSION_RE='^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$'

say() { printf 'install-doof-vault: %s\n' "$*"; }
help() {
  cat <<'HELP'
usage: install-doof-vault.sh [--version X.Y.Z] | --uninstall
  (no options)        install or upgrade to the latest release
  --version X.Y.Z     install that release
  --uninstall         remove ~/.local/bin/doofv and ~/.local/share/doof (keeps ~/.doof: vault and backups)
Downloads from https://github.com/mfdoof/PasswordManager_OOP releases only and verifies SHA256SUMS.
Exit codes: 0 ok, 1 failed (nothing installed), 2 usage error.
HELP
}
die() { printf 'install-doof-vault: error: %s\n' "$*" >&2; exit 1; }
usage_error() {
  printf 'install-doof-vault: %s\n' "$*" >&2
  printf 'usage: install-doof-vault.sh [--version X.Y.Z] | --uninstall\n' >&2
  exit 2
}

# curl restricted to HTTPS, including redirects (release assets redirect to GitHub's download host).
fetch() {
  curl --proto '=https' --proto-redir '=https' --tlsv1.2 -fsSL --retry 3 "$@"
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{ print $1 }'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{ print $1 }'
  else
    die "need sha256sum or shasum to verify the download"
  fi
}

# Expected hash of $2 from SHA256SUMS file $1 (sha256sum format: "<hash>  <name>").
expected_sha256() {
  awk -v f="$2" '$2 == f || $2 == "*" f { print $1; exit }' "$1"
}

# awk reads all input (no early exit), so nothing in the pipe can die of SIGPIPE under pipefail.
java_major() {
  local out
  out="$("$1" -version 2>&1)" || return 0
  printf '%s\n' "$out" | awk -F'"' '/version "/ && !seen { seen = 1; print $2 }' |
    awk -F'[.+-]' '{ if ($1 == "1") print $2; else print $1 }'
}

check_java() {
  local candidate major found=""
  for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java 2>/dev/null || true)"; do
    if [ -z "$candidate" ] || [ ! -x "$candidate" ]; then
      continue
    fi
    major="$(java_major "$candidate")"
    if [[ ! "$major" =~ ^[0-9]+$ ]]; then
      continue
    fi
    found="$found $candidate (Java $major)"
    if [ "$major" -ge "$MIN_JAVA" ]; then
      say "using Java $major at $candidate"
      return 0
    fi
  done
  if [ -n "$found" ]; then
    die "Java $MIN_JAVA or newer is required; found:$found. Install a newer JDK, e.g. https://adoptium.net"
  fi
  die "Java $MIN_JAVA or newer is required and none was found (checked \$JAVA_HOME and PATH). Install one, e.g. https://adoptium.net (macOS: brew install --cask temurin)"
}

latest_version() {
  local url version
  url="$(fetch -o /dev/null -w '%{url_effective}' "$REPO_URL/releases/latest")" ||
    die "cannot reach $REPO_URL/releases/latest"
  version="${url##*/tag/v}"
  [[ "$version" =~ $VERSION_RE ]] || die "could not determine the latest version (got: $url)"
  printf '%s\n' "$version"
}

is_our_launcher() {
  [ -f "$1" ] && grep -qF "$LAUNCHER_MARK" "$1"
}

remove_legacy_launcher() {
  if [ -f "$LEGACY_LAUNCHER" ] && grep -qF "$LEGACY_MARK" "$LEGACY_LAUNCHER"; then
    rm -f "$LEGACY_LAUNCHER"
    say "removed the old launcher $LEGACY_LAUNCHER (the command is now doofv)"
  fi
}

write_launcher() {
  local tmp="$1/doofv"
  cat > "$tmp" <<'DOOF_WRAPPER'
#!/usr/bin/env bash
# DOOF Vault launcher (installed by install-doof-vault.sh). Runs the installed jar with Java 17+.
# Usage: doofv [--vault <path>] [--ascii] [--no-color]    terminal UI
#        doofv backup [--vault <path>] [--out <dir>] [--keep N]
set -euo pipefail

DOOF_JAR="${DOOF_JAR:-$HOME/.local/share/doof/doof-vault.jar}"
MIN_JAVA=17

# Prints the major version of the given java binary, or nothing if it does not run.
# awk reads all input (no early exit), so nothing in the pipe can die of SIGPIPE under pipefail.
java_major() {
  local out
  out="$("$1" -version 2>&1)" || return 0
  printf '%s\n' "$out" | awk -F'"' '/version "/ && !seen { seen = 1; print $2 }' |
    awk -F'[.+-]' '{ if ($1 == "1") print $2; else print $1 }'
}

find_java() {
  local candidate major
  for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java 2>/dev/null || true)"; do
    if [ -z "$candidate" ] || [ ! -x "$candidate" ]; then
      continue
    fi
    major="$(java_major "$candidate")"
    if [[ "$major" =~ ^[0-9]+$ ]] && [ "$major" -ge "$MIN_JAVA" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

if [ ! -f "$DOOF_JAR" ]; then
  echo "doofv: $DOOF_JAR not found. Reinstall with install-doof-vault.sh." >&2
  exit 1
fi

if ! JAVA="$(find_java)"; then
  echo "doofv: Java $MIN_JAVA or newer is required (checked \$JAVA_HOME, then java on PATH)." >&2
  echo "       Install one, e.g. Temurin: https://adoptium.net  (macOS: brew install --cask temurin)" >&2
  exit 1
fi

exec "$JAVA" -jar "$DOOF_JAR" "$@"
DOOF_WRAPPER
  chmod 755 "$tmp"
  mkdir -p "$BIN_DIR"
  mv -f "$tmp" "$LAUNCHER"
  remove_legacy_launcher
}

uninstall() {
  local removed=0
  if is_our_launcher "$LAUNCHER"; then
    rm -f "$LAUNCHER"; removed=1; say "removed $LAUNCHER"
  elif [ -e "$LAUNCHER" ]; then
    say "left $LAUNCHER alone: it was not installed by this script"
  fi
  if [ -f "$LEGACY_LAUNCHER" ] && grep -qF "$LEGACY_MARK" "$LEGACY_LAUNCHER"; then
    removed=1
  fi
  remove_legacy_launcher
  for f in "$JAR" "$VERSION_FILE"; do
    if [ -e "$f" ]; then rm -f "$f"; removed=1; say "removed $f"; fi
  done
  rmdir "$SHARE_DIR" 2>/dev/null || true
  [ "$removed" = 1 ] || say "nothing to remove"
  say "your vault and backups in $HOME/.doof were not touched"
}

path_hint() {
  case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *)
      say "note: $BIN_DIR is not on your PATH. Add this to ~/.zshrc or ~/.bashrc:"
      # shellcheck disable=SC2016  # printed literally, for the user to paste into their shell rc
      printf '    export PATH="$HOME/.local/bin:$PATH"\n'
      ;;
  esac
}

main() {
  local version="" do_uninstall=0
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --version)
        [ "$#" -ge 2 ] || usage_error "--version needs a value"
        version="${2#v}"; shift 2 ;;
      --version=*) version="${1#--version=}"; version="${version#v}"; shift ;;
      --uninstall) do_uninstall=1; shift ;;
      -h|--help) help; exit 0 ;;
      *) usage_error "unknown argument: $1" ;;
    esac
  done
  [ -n "${HOME:-}" ] || die "HOME is not set"

  if [ "$do_uninstall" = 1 ]; then
    [ -z "$version" ] || usage_error "--uninstall does not take --version"
    uninstall
    return 0
  fi
  if [ -n "$version" ] && [[ ! "$version" =~ $VERSION_RE ]]; then
    usage_error "invalid version: $version (expected X.Y.Z)"
  fi
  if [ -e "$LAUNCHER" ] && ! is_our_launcher "$LAUNCHER"; then
    die "$LAUNCHER exists and was not installed by this script; move it away first"
  fi
  command -v curl >/dev/null 2>&1 || die "curl is required"

  check_java
  [ -n "$version" ] || version="$(latest_version)"
  say "installing DOOF Vault $version"

  TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/doof-install.XXXXXX")"
  trap 'rm -rf "$TMP_DIR"' EXIT
  trap 'exit 130' INT TERM  # exit runs the EXIT trap, so the temp dir is always removed

  local name="doof-vault-$version.jar"
  local base="$REPO_URL/releases/download/v$version"
  fetch -o "$TMP_DIR/SHA256SUMS" "$base/SHA256SUMS" || die "cannot download SHA256SUMS for $version (does the release exist?)"
  local expected
  expected="$(expected_sha256 "$TMP_DIR/SHA256SUMS" "$name")"
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || die "SHA256SUMS has no valid entry for $name"

  # Already installed and intact: nothing to download.
  if [ -f "$JAR" ] && [ -f "$VERSION_FILE" ] && [ "$(cat "$VERSION_FILE")" = "$version" ] &&
     [ "$(sha256_of "$JAR")" = "$expected" ]; then
    write_launcher "$TMP_DIR"
    say "DOOF Vault $version is already installed and verified"
    path_hint
    return 0
  fi

  fetch -o "$TMP_DIR/$name" "$base/$name" || die "cannot download $name"
  local actual
  actual="$(sha256_of "$TMP_DIR/$name")"
  if [ "$actual" != "$expected" ]; then
    die "checksum mismatch for $name (expected $expected, got $actual); nothing was installed"
  fi
  say "checksum OK ($expected)"

  mkdir -p "$SHARE_DIR"
  cp "$TMP_DIR/$name" "$SHARE_DIR/.doof-vault.jar.new"
  mv -f "$SHARE_DIR/.doof-vault.jar.new" "$JAR"
  printf '%s\n' "$version" > "$VERSION_FILE"
  write_launcher "$TMP_DIR"

  say "installed $JAR and $LAUNCHER"
  path_hint
  say "run: doofv        (vault: ~/.doof/vault.db; backups: doofv backup)"
}

main "$@"
