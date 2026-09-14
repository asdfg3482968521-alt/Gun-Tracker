import base64, io, tarfile, pathlib, shutil

root = pathlib.Path('.')
parts = [
    root / '.github/upload/chunk00.txt',
    root / '.github/upload/chunk01.txt',
    root / '.github/upload/chunk02.txt',
    root / '.github/upload/chunk03rest.txt',
]
payload = ''.join(p.read_text(encoding='utf-8').strip() for p in parts)
data = base64.b64decode(payload)
with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as tf:
    tf.extractall(root)
shutil.rmtree(root / '.github/upload')
(root / '.github/scripts/apply_uploaded_source.py').unlink()
print('Applied uploaded source package.')
