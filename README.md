# Kangooroo

## Description

Kangooroo is a Java utility for crawling malicious URLs.
It is integrated with [Assemblyline's](https://github.com/CybercentreCanada/assemblyline) [URLDownloader](https://github.com/CybercentreCanada/assemblyline-service-urldownloader) service and can be used as a standalone commandline application.


## Build Instruction
We are using Java 11 with Gradle 7.0.2 for building the Kangooroo Jars. You can also use the gradle wrapper in the project to build the Jar.
To create a uber jar for Kangooroo to run as a standalone utility, run command `gradle shadowJar`. You should be able to find the `KangoorooStandalone.jar` in the build directory.

## Installation
- Have Java version 11 installed
- Have `chromium-browser` and a `chromedriver` that matches the chromium browser version installed (chromedriver can be downloaded from https://chromedriver.chromium.org/downloads)
- Have `chromedriver` and `KangoorooStandalone.jar` placed in the same folder

- if you want to configure simple logging (we have log4j included in the Jar), copy the file at [log4j2.xml](https://github.com/CybercentreCanada/kangooroo/blob/stage/resources/log4j2.xml) to your current directory and add this to your java command `java -Dlog4j2.configurationFile=./log4j2.xml`
- if you want to configure `output_folder` and `temporary_folder` location, copy over the `conf.yml` file and modify the value for `output_folder` and `temporary_folder` to your desired location.

## Usage
The program can be run with command `java -jar KangoorooStandalone.jar [ARGUMENTS]`

Running the program with logging configured:
` java -Dlog4j2.configurationFile=./log4j2.xml -jar KangoorooStandalone.jar [ARGUMENTS]`
- `url` the url that you wish to crawl. It should be full url that starts with "http://" or "https://"
- `url-type` Either `PHISHING` (default) or `SMISHING`. The `PHISHING` options sets the window size to `1280x720` and sets the user-agent to a desktop client. The `SMISHING` option sets the window size to be `375x667` and sets the user-agent to a phone client.

User can specify which directory for output and which directory for storing temporary files by modifying the [conf.yml](https://github.com/CybercentreCanada/kangooroo/blob/stage/resources/conf.yml) file and specify the new conf file with: `-cf path/to/conf/file`

The current default is `./tmp/` directory for temporary files and `./output/` for output result file. These two folder must exist on disk before running the program.


## Output
All files of interest would be in the **output_folder** specified in `conf.yml`. The result of the web crawl is stored in the directory `{output_folder}/{MD5 HASH of URL}/`.
No more zip files in the output directory, instead we have:
- favicon.ico : favicon of website if exist
- screenshot.png : a screenshot of the website of interest
- session.har : the HAR file of the communication with the website
- source.html: the source html file of the website of interest
- result.json: json summary of the url fetch result
