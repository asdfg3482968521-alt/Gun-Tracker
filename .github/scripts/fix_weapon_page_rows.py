from pathlib import Path

path = Path('src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java')
text = path.read_text(encoding='utf-8')
old = '            case HUD -> 9;\n        };'
new = '            case HUD -> 9;\n            case WEAPON -> 5;\n        };'
if old not in text:
    raise SystemExit('expected totalRows switch pattern not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
Path('.github/scripts/fix_weapon_page_rows.py').unlink()
print('Added WEAPON row count to totalRows().')
