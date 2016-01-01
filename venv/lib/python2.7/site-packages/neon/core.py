# -*- coding: utf-8 -*-

RESET = "\x1b[0m"
COLORS = ["\x1b[31m","\x1b[32m","\x1b[33m","\x1b[34m","\x1b[35m","\x1b[36m","\x1b[37m"]

def colorful(s, start=0):
    disp = []
    for i, c in enumerate(s):
        disp.append(COLORS[(i + start) % len(COLORS)] + c + RESET)
    return ''.join(disp)

