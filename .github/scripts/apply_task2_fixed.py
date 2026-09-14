from pathlib import Path
import ast, base64, zlib, os, subprocess

root = Path(__file__).resolve().parents[2]
old_script = root / ".github/scripts/apply_task2.py"
tree = ast.parse(old_script.read_text(encoding="utf-8"))
files = None
for node in tree.body:
    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "FILES" for t in node.targets):
        files = ast.literal_eval(node.value)
        break
if files is None:
    raise RuntimeError("FILES payload not found")

for rel, data in files.items():
    (root / rel).write_bytes(zlib.decompress(base64.b64decode(data)))

old_script.unlink()
Path(__file__).unlink()
subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
subprocess.run(["git", "add", "src/main/java/com/crispywafer/crispywaferguntracker/Config.java", "src/main/java/com/crispywafer/crispywaferguntracker/TargetSelector.java", ".github/scripts"], check=True)
subprocess.run(["git", "commit", "-m", "feat: add sticky lock target filtering"], check=True)
subprocess.run(["git", "push", "origin", f"HEAD:{os.environ['GITHUB_REF_NAME']}"], check=True)
