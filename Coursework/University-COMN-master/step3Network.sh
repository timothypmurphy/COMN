if [ $# -eq 0 ]; then
	echo "Usage: sh ./step3Network.sh PROP_DELAY"
	exit -1
fi

sh ./setupNetwork.sh $1 0.005 10
