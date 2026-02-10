import os

files_to_rename = [
    r'postcss.config.js',
    r'postcss.config.js.bak',
    r'postcss.config.mjs'
]

for file in files_to_rename:
    if os.path.exists(file):
        try:
            os.rename(file, file + '.old')
            print(f"Renamed {file}")
        except Exception as e:
            print(f"Failed to rename {file}: {e}")
    else:
        print(f"File not found: {file}")

with open('styles_test.txt', 'w') as f:
    f.write('Python script ran successfully')
