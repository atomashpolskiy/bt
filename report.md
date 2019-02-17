# Report for assignment 3

This is a template for your report. You are free to modify it as needed.
It is not required to use markdown for your report either, but the report
has to be delivered in a standard, cross-platform format.

## Project

Name: bt

URL: https://github.com/atomashpolskiy/bt

bt is a BitTorrent library for the Java programming language. It supports custom storage backends and many modern BitTorrent features such as magnet links out of the box.

## Onboarding experience

Building was easy and described in the README. Built fine on my Linux system using a modern JDK (OpenJDK 11).

## Complexity

1. What are your results for the ten most complex functions? (If ranking
is not easily possible: ten complex functions)?
   * Did all tools/methods get the same result?
   * Are the results clear?
2. Are the functions just complex, or also long?
3. What is the purpose of the functions?
4. Are exceptions taken into account in the given measurements?
5. Is the documentation clear w.r.t. all the possible outcomes?


### (2,3) ConnectionSource#getConnectionAsync:

TODO: Do a manual count of the CCN.
Purpose, CCN:

The CCN is partially inflated because of branches which are solely there for logging.
The code is fairly long, clocking in at 75 lines for one method and the method is not documented. There are
logging but it does not particularly help in understanding the code.

The purpose of the method is to either return an existing connection to a peer with a certain torrent
or to create a new one to that same peer and torrent.
This new connection is established and run asynchronously through the CompletableFuture class.
To create a new CompletableFuture a lambda is provided in the code. The branches in the lambda should
technically be counted separately, however lizard does not do so.

Code coverage:

According to JaCoCo:
0%
According to manual instrumentation:
0%

### (2,3) MetadataService#buildTorrent
Purpose, CCN:

Many branches here are because of null checks. They are also lonely if-branches without an else-part.
The code creates a torrent from the metadata from some parser (the parser holds the content of some torrent file).
There is no real documentation to establish the entirety of possible outcomes and warnings of rawtypes and unchecked exceptions are suppressed.

Code coverage:

According to JaCoCo:
0%
According to manual instrumentation:


## Coverage

### Tools

The project uses JaCoCo for coverage and already had it integrated with its build environment.
How to use the tool in this project was completely undocumented so at first I had to look around for
instructions on how to use the tool through Google. I did this for about 2 hours without any real gains being made.
Finally I found a Travis script in the scripts folder called "travis-run-tests.sh" which ran the tests with code coverage on.
So yes, the experience in using this tool was terrible because of a lack of in-project documentation.
The JaCoCo project's documentation however was fine.


//Document your experience in using a "new"/different coverage tool.

//How well was the tool documented? Was it possible/easy/difficult to integrate it with your build environment?

### DYI

ConnectionSource#getConnectionAsync:
See: https://gist.github.com/jsjolen/0e98536c5817fc0502eeb954b003a7fc
MetadataService#buildTorrent:
See: https://gist.github.com/jsjolen/fb3bc47617056cac7d605e7da5f53842

You can generate the patch file for these two methods with: git format-patch -1 899854761449388bf0829fbc272e17c8276e854e


//Show a patch that show the instrumented code in main (or the unit
//test setup), and the ten methods where branch coverage is measured.

//The patch is probably too long to be copied here, so please add
//the git command that is used to obtain the patch instead

>What kinds of constructs does your tool support, and how accurate is its output?

Our manual tool consists of one single method which writes some numerical id to a file with some name.
The numerical id represents the branch taken. Assume a strong adversary, could such an adversary avoid having our method called or its output recorded in the program when a branch occurs?
As far as we can discern the answer is no. Since our method is statically called there's no way to override the method using reflection for example. Therefore the tool is 100% accurate.
However it's still a tool of limited usage because of the large amount of manual work needed to discern which branches were taken and which weren't.


### Evaluation

Report of old coverage: [link]

Report of new coverage: [link]

Test cases added:

git diff ...

## Refactoring

Plan for refactoring complex code:

ConnectionSource#getConnectionAsync:



MetadataService#buildTorrent

Carried out refactoring (optional)

git diff ...

## Effort spent

For each team member, how much time was spent in

1. plenary discussions/meetings;
Johan: 2 hours
Nikhil: 2 hours
Jagan: 2 hours
Tom: 0 hours (was ill)

2. discussions within parts of the group;
Johan: 10 minutes or so

3. reading documentation;
Johan: 1 hour

4. configuration;

5. analyzing code/output;
Johan: 4 hours

6. writing documentation (writing report?));
Johan: 3 hours

7. writing code;
Johan: 1 hour

8. running code?
Johan: Many hours?

## Overall experience

What are your main take-aways from this project? What did you learn?

Is there something special you want to mention here?
