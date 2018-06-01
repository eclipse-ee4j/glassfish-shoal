---++ Goal
   * Abstracting out the transport layer of Shoal


---++ Motive
   * Current Shoal are using JXTA as group communication technology 
   * But most of Shoal's clustering functionality like leadership, failure and discovery are tightly coupled to JXTA
   * If we want to change JXTA into JGroup, Grizzly or other transport layer, should we implement a separate clustering functionality?
   * I would like to say NO. The initiative intention of Shoal is that users should be able to integrate any group communication modules easily through SPI.


---++ Current status
   * Shoal GMS utilizes JxtaManagement component(a JXTA based group service provide) for dynamic cluster configuration, formation and monitoring.
   * Main components
      * ClusterManager
         * Manages lifecycle of this SPI
         * Sends application messages to other members and receives them
      * MasterNode
         * Has a lightweight protocol allowing a set of nodes to discover one another and autonomously elect a master for the cluster
         * Resolves master's collisions
         * Sends a cluster view to other members and received them
      * HealthMonitor
         * Has a lightweight protocol allowing a set of nodes to monitor the health of a cluster
         * becomes aware of indoubt or failure of members
         * Sends health messages to other members and received them
      * SystemAdvertisement
         * An extensible XML document describing system characteristics(HW/SW configuration, CPU load and etc...)
         * Represents the symbol of the unique member


---++ Improvement
   * Functions and implementations related to the transport layer could be separated from ClusterManager, MasterNode, HealthMonitor
   * A common transport manager which is called by NetworkManager could be in charge of their functions.
   * SystemAdvertisement could become to be not a class but a interface. So all modules can use the interface for getting the specification of any members.
   * The network entity which is dependent on a transport layer like net.jxta.peer.PeerID could be replaced by PeerID which Shoal itself contains.
   * The network message which is dependent on a transport layer like net.jxta.endpoint.Message could be replaced by Message which Shoal itself contains.


---++ Details
   * Repackage
      * com.sun.enterprise.ee.cms.impl.jxta.* -> com.sun.enterprise.ee.cms.impl.base.*
         * com.sun.enterprise.ee.cms.impl.base.SystemAdvertisement interface
         * com.sun.enterprise.ee.cms.impl.base.SystemAdvertisementImpl
            * Implements default SystemAdvertisement which is serializable
         * com.sun.enterprise.ee.cms.impl.base.PeerID
            * Has group name and instance name and unique id which is serializable with generic type
         * com.sun.enterprise.ee.cms.impl.Utility
            * Can be used by any calling code to do common routines

      * com.sun.enterprise.jxtamgmt.* --> com.sun.enterprise.mgmt.*
         * Has ClusterManager, MasterNode, HealthMonitor and etc... which has common clustering functionality.

      * com.sun.enterprise.mgmt.transport.*
         * Have interfaces of a transport specification for common cluster management
         * com.sun.enterprise.mgmt.transport.Message interface
         * com.sun.enterprise.mgmt.transport.MessageImpl
            * Implements default Message which is based on ByteBuffer
            * Supports addMessageElement(), getMessageElement() and removeMessageElement() with key-value pair like net.jxta.endpoint.Message
         * com.sun.enterprise.mgmt.transport.MessageListener interface
         * com.sun.enterprise.mgmt.transport.MessageSender interface
            * Has send( PeerID, Message ) API
         * com.sun.enterprise.mgmt.transport.MulticastMessageSender interface
            * Has broadcast( Message ) API
         * com.sun.enterprise.mgmt.transport.NetworkManager interface
            * Extends MulticastMessageSender and MessageSender
            * All cluster modules in Shoal can use this for transfering messages.
            * Support multicast, TCP and UDP sending API
         * com.sun.enterprise.mgmt.transport.Abstract*
            * Help you to implement interfaces easily
            * Have common or specific logic of transport layers

      * com.sun.enterprise.mgmt.transport.grizzly
         * Has the implementation of Grizzly transport layer
         * Implements most of interfaces of com.sun.enterprise.mgmt.transport
         
      * com.sun.enterprise.mgmt.transport.jxta
         * Has the implementation of JXTA transport layer
         * Implements most of interfaces of com.sun.enterprise.mgmt.transport


---++ Others
   * Using Grizzly
      * Grizzly version 1.9.17
      * com.sun.enterprise.mgmt.transport.grizzly.*
      * Unfortunately, current Grizzly doesn't support multicast
      * So I implements NIO.2's multicast which is integrated into Grizzly on JDK7
      * And I also implements a blocking multicast server for supporting JDK5 or 6
      * The multicast server will be switched automatically according to runtime JDK version
      * The connections will be cached like jxta's output cache. I used the CacheableConnectorHandlerPool of Grizzly
      * For configuration
         * You can use system property or property map
         * See the GrizzlyConfigConstants
         * If you set "-DTCPSTARTPORT=9090 -DTCPENDPORT=9120" with system property, an available TCP port between TCPSTARTPORT and TCPENDPORT will be used for listening, sending and receiving
         * Set "-DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly" with system property
         * If you would like to default configurations, See GrizzlyNetworkManager#configure()

   * Using JXTA
      * Most of packages and classes are not changed as well as not removed, so if you run Shoal by default, the behavior is same to old version.
         * Run by default or set "-DSHOAL_GROUP_COMMUNICATION_PROVIDER=jxta" with system property
      * I created new packages for supporting JXTA on this Shoal version to which abstraction is applied  
         * Set "-DSHOAL_GROUP_COMMUNICATION_PROVIDER=jxtanew" with system property
         * com.sun.enterprise.mgmt.transport.jxta.*
         * Most of original algorithms are also preserved.

---++ How to compile
   * Required JDK5 or JDK6 or JDK7
   * Use the ant script and shoal/gms/build.xml which has been modified simply
      
---++ How to run
   * Required additional grizzly-framework.jar and grizzly-utils.jar libraries
   * You can test this simply with SimpleJoinTest.java. (If you run on windows, replace ':' with ';')
      * For using grizzly transport
         * java -cp build:lib/grizzly-framework.jar:lib/grizzly-utils.jar -DTCPSTARTPORT=9090 -DTCPENDPORT=9120 -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly com.sun.enterprise.shoal.jointest.SimpleJoinTest server1
         * If you run this on JDK7, NIO.2 multicast channel used. Otherwise, blocking multicast server used
      * For using original jxta transport
         * java -cp build:lib/jxta.jar -DSHOAL_GROUP_COMMUNICATION_PROVIDER=jxta com.sun.enterprise.shoal.jointest.SimpleJoinTest server1
         * java -cp build:lib/jxta.jar com.sun.enterprise.shoal.jointest.SimpleJoinTest server1
         * Same to original Shoal
      * For using new jxta transport
         * java -cp build:lib/jxta.jar -DSHOAL_GROUP_COMMUNICATION_PROVIDER=jxtanew com.sun.enterprise.shoal.jointest.SimpleJoinTest server1


---++ Tests
   * Simple gms functions of Shoal are tested like join, shutdown, failure, DSC, application message's sending and receiving

   * New automated 10 member cluster Developer level test
     Run following command:
     % runsimulatecluster.sh <transport>

    <transport> can be grizzly, jxtanew or jxta.   grizzly is the default for this script.
    This run produces 11 server log files, server.log and instance[01-10].log.

    After the run has completed, run following command to analyze the results in the log files. 
    % analyzelogs.sh

    The output shows all JOINED, JOINED_AND_READY and PLANNED_SHUTDOWN events recevied by Master server and then each member of 10 instance cluster.

---++ TODO
   * Various tests are needed on complicated environments
   * Packages and class names should be reviewed
   * Should consider that cluster members are located beyond one subnet or multicast traffic is disabled [DONE]
      * VirtualMulticastSender class supports this, but I think that more intelligent logic is needed. i.g. JXTA's Rendezvous function.
   * etc...
   
