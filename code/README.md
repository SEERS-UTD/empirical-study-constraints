# Tool for Semi-Automated Constraint Tracing

We include the source code of the tool used to locate additional enforcing statements for the constraints in our data set, as detailed in section 6.1 of the article.

## Requirements

1. *OpenJDK 11*. Tested with openjdk-11.0.13 on Linux Mint 20.3
2. *JAVA_HOME variable set*. Set it to the home directory of the JDK installation, for example: `export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64`

## Instructions

You can use Gradle (`gradlew` or `gradlew.bat` depending on your operating system) to easily build and run the project.

To see the options available run:

        ./gradlew run --args="-h"
        
To obtain the traces used for the study in section 6 of the submission:

1. Ensure that your current working directory is the directory where *this* README is located.
2. Run:

        ./gradlew run --args="-r 2 ../../detector-inputs.csv ./programs ../trace-results" -PjvmArgs="-Xms1g -Xmx10g"
        
   The results will be in the `trace-results` directory.
