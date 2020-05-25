# WorldKit
A desktop application for generating 3D worlds for games and multimedia.

To run WorldKit follow these steps:
* Clone the repository from github. I recommend using Github Desktop via the link on this page, but it can be cloned other ways.
* Download and install IntelliJ IDEA Community (https://www.jetbrains.com/idea/). This is optional but I will only cover the steps to run WorldKit from this IDE.
* Download and install OpenJDK 11 (https://jdk.java.net/java-se-ri/11). It probably runs with other JVMs, but I only tried it with this version.
* Open build.gradle as a project in IntelliJ IDEA.
* Create a new run configuration in IntelliJ IDEA. For Main class, browse and select `Main`. Under VM options use `-Xms4g -Xmx16g -Dwk.local.app.dir="C:\Users\x\AppData\Local\WorldKit" -Dwk.local.doc.dir="C:\Users\x\Documents\WorldKit" -Dwk.log.to.sys.out=true`. Replace `x` with your username where appropriate, and make sure these directories exist. Choose a Working directory. For Use classpath of module, select `gec.main`. For JRE select jdk 11. You may need to set it up as a JDK in IntelliJ. Click OK when finished.
* Download the zip containing terrain amplification dictionaries from this page, and unzip them in the `wk.local.app.dir`. You could generate amplification dictionaries yourself using the code in this repo, but I recommend starting with the provided dictionaries.
* Click the run button beside the run configuration to launch WorldKit.
* The first time it loads it will take some time to generate and cache content it needs for world building.

# Videos
https://youtu.be/CAYgW5JfCQw?list=PLiM50pyfNRg_zPv4uYIF5TOqdD8bZdqd7
