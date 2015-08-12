package Impl

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import java.util.logging.SimpleFormatter
import java.util.logging.FileHandler
import java.util.logging.Logger

/**
 * I make this main class to test the paxos behavior, once users run this class, it set up a 
 * paxos server with a selected id. In this example, I assume there are 5 replicated machiness
 * within a system, the information of the replicated machiens is in the paxos.config. I am using
 * host name '127.0.0.1' for each node. users should run at least 3 nodes with ids among 1-5 to
 * be able to propose a value.
 * At the same time, users can use this main class as a client to ask each existing paxos to propose
 * a value by simply run command 'propose' from the console.
 * 
 * @author zepeng zhao
 */

object Main extends App{
  override def main(args:Array[String]){
     var logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)
     logger.setUseParentHandlers(false);
     var fileTxt = new FileHandler("log_3.txt");
     var formatterTxt = new SimpleFormatter();
     fileTxt.setFormatter(formatterTxt);
     logger.addHandler(fileTxt);
       
    val system = ActorSystem("RemoteSystem")
    
    var id = -1
    
    try{
      var m = Util.loadPaxos()
      while(id < 0){
        try{
          print("Select node id:")
          id = readLine().toInt
          if(!(id > 0 && id <=m.size)){
            println("id should be a number between 1 and " +m.size)
            id = -1
         }
        }
        catch{
          case e:Exception => println("nid should be a number between 1 and " +m.size); id = -1
        }
      }      
      var master = system.actorOf(Props(classOf[Paxos_Actor],m,id), "Paxos")
                 
      while(true){
        print("->")
        var command = readLine()
        if(command.equals("propose")){
          print("value:")
          var v = readLine().asInstanceOf[java.io.Serializable]
          print("node:")
          try{
            var nid = readLine().toInt
            if(nid > 0 && nid <=m.size)
              master ! propose(v,nid)
            else
              println("nid should be a number between 1 and " +m.size)
          }catch{
            case e:Exception => println("nid should be a number between 1 and " +m.size)
          }
        }  
        
      }
    }    
    catch{
      case e:Exception => println(e.getMessage)
    } 
  }

}