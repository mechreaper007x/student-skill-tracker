import os

files_to_delete = [
    r'c:\Users\Savyasachi Mishra\Desktop\student-skill-tracker\frontend\postcss.config.js',
    r'c:\Users\Savyasachi Mishra\Desktop\student-skill-tracker\frontend\postcss.config.js.bak',
    r'c:\Users\Savyasachi Mishra\Desktop\student-skill-tracker\frontend\postcss.config.mjs'
]

for file in files_to_delete:
    if os.path.exists(file):
        try:
            os.remove(file)
            print(f"Deleted {file}")
        except Exception as e:
            print(f"Failed to delete {file}: {e}")
    else:
        print(f"File not found: {file}")
