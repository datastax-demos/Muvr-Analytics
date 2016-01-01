#!/bin/bash
set -xe

# Install pip
if ! [ `command -v pip` ]
then
  sudo easy_install pip
fi

# Create virtual env
VENV=venv
sudo pip install virtualenv
rm -rf $VENV
virtualenv $VENV -p /usr/bin/python2.7
source $VENV/bin/activate

# Build NumPy Wheel
pip wheel numpy==1.10.1

# Install NumPy using Wheel
pip install numpy==1.10.1

# Build SciPy Wheel
pip wheel scipy

# Install dependencies
pip install --use-wheel -r muvr.pip

# Insteall neon latest
git clone https://github.com/NervanaSystems/neon.git $VENV/neon
cd $VENV/neon
make sysinstall
cd -

# Install mlp
cd mlp
python setup.py install
cd -
