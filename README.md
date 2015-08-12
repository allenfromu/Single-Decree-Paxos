#Paxos Implementation
##Overview
Paxos is a neat algorithm to solve the consensus problem. It was firstly described in <a href="http://www.lamport.org" target="_blank">Leslie Lamport's </a> paper (<a href="http://research.microsoft.com/en-us/um/people/lamport/pubs/paxos-simple.pdf" target="_blank">Paxos Made Simple</a>). Paxos is considered as one of the hardest algorithms to understand, however, I have to confess it is really a neat algorithm after I finally figured out how it works to avoid conflicts when different values are proposed at almost the same time from different clients and make all the nodes in a system agree with only one value. Here we assume that every node will just stop if it fails, and we don't need to worry about Byzantine failure. 

##Run the project using <a href="http://www.scala-sbt.org" target="_blank">SBT</a> (Simple Build Tool)
 * Install SBT on your computer.
  * For mac, you can install sbt using homebrew, from terminal, run command line: 
   ```
     ->brew install sbt
   ```
  * For more instructions about installing SBT on any type of OS, visit <a href="http://www.scala-sbt.org/release/tutorial/Setup.html" target="_blank">this link</a>
 * Clone the project from repo: https://github.com/allenfromu/Single-Decree-Paxos.git
 * Go to the Single-Decree-Paxos directory from terminal and then run command: 
 ``` 
 -> sbt run
 ```


