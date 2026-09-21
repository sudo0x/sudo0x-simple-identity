# Git Push Permission and SSH Troubleshooting

This guide explains how to fix a GitHub push failure such as:

```text
remote: Permission to sudo0x/sudo0x-simple-identity.git denied to dancypher.
fatal: unable to access 'https://github.com/sudo0x/sudo0x-simple-identity.git/':
The requested URL returned error: 403
```

## What the error means

The repository remote is using **HTTPS**, not SSH:

```text
https://github.com/sudo0x/sudo0x-simple-identity.git
```

Git is therefore using the GitHub account cached by the credential manager. In this
case, that account is `dancypher`, which does not have permission to push to the
`sudo0x` repository.

The SSH key configuration is not used while the remote starts with `https://`.

## Recommended fix: use the SSH remote

The SSH configuration uses this alias:

```sshconfig
Host github.com-sudo0x
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519_sudo0x
    IdentitiesOnly yes
```

The SSH config is a **file**, not a directory. View it with:

```bash
cat ~/.ssh/config
```

From the project directory, change `origin` to use the SSH alias:

```bash
git remote set-url origin git@github.com-sudo0x:sudo0x/sudo0x-simple-identity.git
```

Confirm that the remote changed:

```bash
git remote -v
```

Expected output:

```text
origin  git@github.com-sudo0x:sudo0x-simple-identity.git (fetch)
origin  git@github.com-sudo0x:sudo0x-simple-identity.git (push)
```

Test SSH authentication:

```bash
ssh -T github.com-sudo0x
```

Successful authentication looks similar to:

```text
Hi sudo0x! You've successfully authenticated, but GitHub does not provide shell access.
```

Push the branch:

```bash
git push -u origin master
```

## Quick diagnosis checklist

### 1. Check which remote protocol is configured

```bash
git remote -v
```

- `https://github.com/...` means Git uses HTTPS credentials.
- `git@github.com...` means Git uses SSH.

### 2. Check the current branch and working tree

```bash
git status
```

Having a clean working tree does not affect permission errors. A clean tree only
means there are no uncommitted changes.

### 3. Test the SSH key directly

```bash
ssh -T github.com-sudo0x
```

If this authenticates as `sudo0x`, the SSH key is working. The project remote
still needs to be changed if `git remote -v` shows HTTPS.

## Alternative: continue using HTTPS

If SSH is not desired, use a GitHub personal access token instead of a password.
The token must have permission to write to the repository. Remove the old
`dancypher` GitHub credential from **Windows Credential Manager**, then retry:

1. Open **Credential Manager**.
2. Select **Windows Credentials**.
3. Remove the GitHub credential related to `github.com`.
4. Run:

   ```bash
   git push origin master
   ```

5. Sign in with the GitHub account that has write access and use a personal
   access token when Git asks for a password.

Using the SSH remote is usually simpler because it avoids cached HTTPS account
credentials.

## What credential information is visible

From the configuration and command output, the following information is visible:

- GitHub username used by the SSH alias: `sudo0x`
- SSH alias: `github.com-sudo0x`
- Private-key **file path**: `~/.ssh/id_ed25519_sudo0x`
- Public-key file path: `~/.ssh/id_ed25519_sudo0x.pub`
- Repository: `sudo0x/sudo0x-simple-identity`

The private key contents are not shown here. Never share the contents of
`id_ed25519_sudo0x`, its passphrase, GitHub tokens, or passwords. The `.pub`
file is intended to be uploaded to GitHub, but it should still only be shared
when necessary.

## Change the GitHub credential used by SSH

If you want Git to use another GitHub account, create a separate SSH key for
that account. Do not overwrite a key that is still used by another account.

### 1. Create a new key pair

Run this in Git Bash and replace the email with the email associated with the
target GitHub account:

```bash
ssh-keygen -t ed25519 -C "your-email@example.com" -f ~/.ssh/id_ed25519_github_new
```

Choose a strong passphrase when prompted. This creates:

- `~/.ssh/id_ed25519_github_new` — private key; keep secret
- `~/.ssh/id_ed25519_github_new.pub` — public key; upload this to GitHub

### 2. Add the public key to the target GitHub account

Display only the public key:

```bash
cat ~/.ssh/id_ed25519_github_new.pub
```

In GitHub, open **Settings → SSH and GPG keys → New SSH key**, give it a
recognizable title, paste the public key, and save it.

### 3. Add a separate SSH alias

Edit `~/.ssh/config`:

```bash
notepad ~/.ssh/config
```

Add an alias for the new account:

```sshconfig
Host github.com-new-account
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519_github_new
    IdentitiesOnly yes
```

Use a different alias for each GitHub account. The alias is how Git selects the
correct key when all accounts use `github.com`.

### 4. Test the new account

```bash
ssh -T github.com-new-account
```

The response should identify the intended GitHub username. GitHub may say that
it does not provide shell access; that message is normal for a successful SSH
authentication.

### 5. Change this project to the new account

From the project directory:

```bash
git remote set-url origin git@github.com-new-account:sudo0x/sudo0x-simple-identity.git
git remote -v
git push -u origin master
```

The target GitHub account must have write access to the repository. If it does
not, add that account as a collaborator or use a repository where it has access.

## Remove the old credential

Only remove the old key after confirming the new key works:

1. Remove the old public key from **GitHub Settings → SSH and GPG keys**.
2. Remove the old `Host github.com-sudo0x` entry from `~/.ssh/config` if it is
   no longer needed.
3. Delete the old local key files only if you are certain no other project uses
   them:

   ```bash
   rm ~/.ssh/id_ed25519_sudo0x ~/.ssh/id_ed25519_sudo0x.pub
   ```

Do not delete the old key if another repository or computer still depends on it.

## Common mistakes

### Trying to enter the SSH config file as a directory

This fails because `config` is a file:

```bash
cd ~/.ssh/config
```

Use one of these instead:

```bash
cat ~/.ssh/config
notepad ~/.ssh/config
```

### Changing the SSH config but not the Git remote

Updating `~/.ssh/config` does not automatically change existing Git remotes.
Always run:

```bash
git remote set-url origin git@github.com-sudo0x:sudo0x/sudo0x-simple-identity.git
```

### Authenticating successfully but still receiving a push denial

`ssh -T` only proves which SSH account authenticated. A push can still fail if:

- the repository remote points to the wrong repository;
- the authenticated GitHub account lacks write access;
- the branch is protected; or
- the repository belongs to an organization with additional access rules.

Check the remote and repository access first:

```bash
git remote -v
ssh -T github.com-sudo0x
```
