# Перекодирует звуки в ogg/vorbis средствами звуковой библиотеки Blender.
# Запускается не сам по себе, а из convert-pack.ps1:
#   blender --background --factory-startup --python convert_sounds.py -- jobs.json
#
# jobs.json — список пар {"source": ..., "target": ...}. Звуки сводятся в моно:
# стереозвук Minecraft играет без учёта расстояния и направления.

import json
import os
import sys

import aud

jobs_path = sys.argv[sys.argv.index("--") + 1]

with open(jobs_path, encoding="utf-8-sig") as jobs_file:
    jobs = json.load(jobs_file)

failed = 0

for job in jobs:
    try:
        os.makedirs(os.path.dirname(job["target"]), exist_ok=True)
        sound = aud.Sound(job["source"]).rechannel(1)
        rate = int(sound.specs[0])
        sound.write(job["target"], rate=rate, channels=1, format=aud.FORMAT_S16,
                    container=aud.CONTAINER_OGG, codec=aud.CODEC_VORBIS, bitrate=96000)
        print("TF2CONV ok " + job["target"])
    except Exception as error:  # один битый файл не должен останавливать остальные
        failed += 1
        print("TF2CONV fail " + job["source"] + ": " + str(error))

print("TF2CONV done, failed: " + str(failed))
sys.exit(1 if failed else 0)
