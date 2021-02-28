#!/bin/bash

if [ $# -lt 1 ]; then
	echo "Usage: ./receiver.sh receiver_number [parameters]"
	exit
fi

if [ $1 -lt 1 -o $1 -gt 4 ]; then
	echo "Error: receiver_number must be between 1 and 4!"
	exit
fi

NUM=$1
shift

export CLASSPATH=$CLASSPATH:bin/
java Receiver$NUM $@
