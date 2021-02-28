#!/bin/bash

# Defaults
DELAY=10
LOSS=0
BANDWIDTH=10

if [ $# -gt 0 ]; then
	if [ $# -gt 1 ]; then
		if [ $# -gt 2 ]; then
			BANDWIDTH=$3
		fi
		LOSS=$2
	fi
	DELAY=$1
fi

ipfw -f flush > /dev/null

echo "Running commands:"
echo "ipfw add pipe 100 in"
echo "ipfw add pipe 200 out"
echo "ipfw pipe 100 config delay ${DELAY}ms plr ${LOSS} bw ${BANDWIDTH}Mbits/s"
echo "ipfw pipe 200 config delay ${DELAY}ms plr ${LOSS} bw ${BANDWIDTH}Mbits/s"
echo "End commands."
echo ""

ipfw add pipe 100 in
ipfw add pipe 200 out

ipfw pipe 100 config delay ${DELAY}ms plr ${LOSS} bw ${BANDWIDTH}Mbits/s
ipfw pipe 200 config delay ${DELAY}ms plr ${LOSS} bw ${BANDWIDTH}Mbits/s
