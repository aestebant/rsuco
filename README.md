# RSUCO: Recommendation System for the University of Córdoba

Associated repository to the paper *Helping university students to choose elective courses by using a hybrid multi-criteria recommendation system with genetic optimization* published in [Knowledge-Based System journal](https://doi.org/10.1016/j.knosys.2019.105385) containing the source code of the proposed **Hybrid Multi-Criteria Recommendation System**.

RSUCO is a course recommendation system designed to provide personalized recommendations to university students using a variety of techniques,  such as collaborative filtering, content-based, and text-based techniques among others.

This repository provides the readers of the paper with the necessary instructions to access the source code of the proposal and to obtain the recommendations themselves, as long as they have a database with the appropriate format.

## Dataset

The dataset used to conduct this study is available in this [repository](https://www.uco.es/kdis/course-recommendation-dataset/), corresponding to 2500 entries from 95 students and 63 courses collected from the Computer Engineering Bachelor program at University of Córdoba and properly anonymized.

## Project structure

* `src/main/java`: Contains the main source code of the project, structured in packages:
  * `core`: Main classes to run and evaluate the recommender system.
  * `evaluator`: Classes related to performance evaluation and analysis.
  * `recommender`: Implementations of different recommendation algorithms.
  * `util`: Utilities to load configurations, manage models, and handle MySQL database connections.
* `configuration`: Configuration files to customize algorithms, evaluations and system parameters.
* `pom.xml`: Maven configuration file to manage project dependencies.

## Requirements

The proposed `HybridRC` is developed in Java and using Maven, so these tools must be installed:

* **Java Development Kit JDK 8 or later**: developing language.
* **Apache Maven**: for dependencies management.
* **MySQL**: for data storage.

To confirm that these dependencies are installed, the command `mvn --version` should produce an output similar to:

```bash
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: /opt/apache-maven-3.9.9
Java version: 1.8.0_45, vendor: Oracle Corporation
Java home: /Library/Java/JavaVirtualMachines/jdk1.8.0_45.jdk/Contents/Home/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "mac os x", version: "10.8.5", arch: "x86_64", family: "mac"
```

## Installation

Once the system is configured, the following steps will install the library:

1. Clone the repository:

```bash
git clone https://github.com/aestebant/rsuco.git
```

2. Open the generated folder `rsuco` in any Java editor (IntelliJ, VSCode...).
3. Follow the instructions to load the `pom.xml` file that defines all the characteristics of the project, including the dependencies necessary for the library.
4. Download the project dependencies:

```bash
mvn install
```

Once the project is loaded and thanks to Maven, the following operations can be performed:

* **validate**: validate the project is correct and all necessary information is available
* **compile**: compile the source code of the project
* **test**: test the compiled source code using a suitable unit testing framework. These tests should not require the code be packaged or deployed
* **package**: take the compiled code and package it in its distributable format, such as a JAR.
* **integration-test**: process and deploy the package if necessary into an environment where integration tests can be run
* **verify**: run any checks to verify the package is valid and meets quality criteria
* **install**: install the package into the local repository, for use as a dependency in other projects locally
* **deploy**: done in an integration or release environment, copies the final package to the remote repository for sharing with other developers and projects.

There are two other Maven lifecycles of note beyond the default list above. They are:

* **clean**: cleans up artifacts created by prior builds
* **site**: generates site documentation for this project

## Usage

To run a recommendation the user should run the `com.uco.rs.core.RunRS` that receive two arguments: i) connection to the database with the users and courses information and ii) configuration of the recommendation system to use:

```bash
java -cp target/rsuco.jar com.uco.rs.core.RunEval "configuration/Model.xml" "configuration/CHCOptimizedRS.xml"
```

## Reference

A. Esteban, A. Zafra, and C. Romero, “Helping university students to choose elective courses by using a hybrid multi-criteria recommendation system with genetic optimization,” *Knowledge-Based Systems*, vol. 194, pp. 1–14, 2020, doi: [10.1016/j.knosys.2019.105385](https://doi.org/10.1016/j.knosys.2019.105385).

```tex
@article{Esteban2020kbs,
   author = {Aurora Esteban and Amelia Zafra and Cristóbal Romero},
   doi = {10.1016/j.knosys.2019.105385},
   issn = {09507051},
   journal = {Knowledge-Based Systems},
   pages = {1-14},
   title = {Helping university students to choose elective courses by using a hybrid multi-criteria recommendation system with genetic optimization},
   volume = {194},
   year = {2020},
}

```
