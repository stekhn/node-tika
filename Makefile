.DELETE_ON_ERROR:

JAVAS := $(shell ls java/cg/m/nodejs/tika/*.java)

install: jar/Tika.jar node_modules

jar/Tika.jar: $(JAVAS) build/java
	if [ ! -d build/tika ]; then \
		$(error Tika not present in build/tika. Unable to build.) \
	fi;
	javac -d build/java -cp build/tika/tika-core/target/tika-core-1.5-SNAPSHOT.jar:build/tika/tika-core/target/tika-parsers-1.5-SNAPSHOT.jar $(JAVAS)
	cd build/java && jar cvf ../../$@ -C . .

build/java:
	if [ ! -d $@ ]; then \
		mkdir -p $@; \
	fi

node_modules: package.json
	npm install
	touch $@

test: node_modules
	./node_modules/.bin/mocha --timeout 30000 --reporter spec --check-leaks --ui tdd --recursive

.PHONY: install test
