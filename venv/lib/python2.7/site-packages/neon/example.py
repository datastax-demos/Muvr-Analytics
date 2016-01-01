# -*- coding: utf-8 -*-

import sys
import time

from core import colorful

if __name__ == "__main__":
    s = "NEON is colorful cui utility"
    for i in xrange(0, len(s) + 1):
        sys.stdout.write(colorful(s[:i], start=i))
        sys.stdout.flush()
        time.sleep(0.5)
        sys.stdout.write("\r")
