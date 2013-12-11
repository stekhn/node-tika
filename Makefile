.DELETE_ON_ERROR:

JAVAS := $(shell ls src/main/java/cg/m/nodetika/*.java)
JAR := jar/node-tika-1.5-SNAPSHOT.jar
PARSERS_JAR := build/tika/tika-core/target/tika-parsers-1.5-SNAPSHOT.jar

install: node_modules

update: update-tika ($JAR)

($JAR): $(JAVAS) build/java
	mvn install

$(PARSERS_JAR): build/tika
	 cd $< && mvn clean && mvn install -Dmaven.test.skip=true
	 touch $<

update-tika build/tika:
	if [ ! -d build/tika ]; then \
		git clone git://git.apache.org/tika.git build/tika; \
	else \
		cd build/tika && git pull; \
		touch .; \
	fi

build/java:
	if [ ! -d $@ ]; then \
		mkdir -p $@; \
	fi

node_modules: package.json
	npm install
	touch $@

test: node_modules
	./node_modules/.bin/istanbul cover ./node_modules/.bin/_mocha -- --timeout 3000 --reporter spec --check-leaks --ui tdd --recursive

clean:
	rm -rf coverage build node_modules

.PHONY: install update update-tika test clean
