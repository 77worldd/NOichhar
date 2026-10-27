"""Surface compiler and test failures as GitHub check annotations."""
import glob
import re
import sys
import xml.etree.ElementTree as ET


def annotate(message):
    message = str(message).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{message[:4000]}')


try:
    lines = open(sys.argv[1], encoding='utf-8', errors='replace').readlines()
except OSError:
    lines = []
interesting = re.compile(r'(^\s*e: |error:|FAILURE:|Execution failed|What went wrong|^\s*> .+| FAILED)', re.I)
for line in [line.strip() for line in lines if interesting.search(line)][-30:]:
    annotate(line)
for path in glob.glob(sys.argv[2], recursive=True):
    try:
        root = ET.parse(path).getroot()
        for case in root.iter('testcase'):
            for failure in list(case.findall('failure')) + list(case.findall('error')):
                annotate(f"{case.get('classname', '')}.{case.get('name', '')}: {failure.get('message', '')} {failure.text or ''}")
    except (ET.ParseError, OSError):
        pass
