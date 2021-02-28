#!/bin/bash

if [ $# -lt 1 ]; then
	echo "Usage: ./sender.sh sender_number [parameters]"
	exit
fi

if [ $1 -lt 1 -o $1 -gt 4 ]; then
	echo "Error: sender_number must be between 1 and 4!"
	exit
fi

NUM=$1
shift

export CLASSPATH=$CLASSPATH:bin/
java Sender$NUM $@
