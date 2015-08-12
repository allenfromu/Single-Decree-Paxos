package Impl

import akka.actor.Actor
import akka.io.Udp
import java.net.InetSocketAddress
import akka.io.IO
import akka.actor.ActorRef
import java.util.logging.Logger
import java.io.Serializable
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.io.Source
import java.io.FileWriter

case class receive_prepare(pid:ProposalID)
case class receive_promise(nid:Int, accepted_value:Serializable,accepted_id:ProposalID, promised_pid:ProposalID)
case class receive_nack(highest_pid:ProposalID)
case class send_prepare()
case class propose(value:Serializable,nid:Int)
case class accept(acc_value:Serializable, acc_pid:ProposalID )
case class accepted( uid:Int, value:Serializable, pid:ProposalID)
case class propose_cmd(v:Serializable)
case class come_to_consensus(ins:BigInt)

/**
 * A paxos actor can act as three types of agents: proposer, acceptor, learner.
 * As proposer, users can prompt the paxos to propose a value from the console
 * As acceptor, the paxos can also receive prepare message, and send out promise or nack message as response
 * As learner, the paxos is always listening from other paxos accepted value.
 * 
 * @author Zepeng Zhao
 * 
 */
class Paxos_Actor(val pm:Map[Int,InetSocketAddress], val id:Int) extends Actor{   
   private val logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)
   import context.system
   private var proposal_id:ProposalID = null
   private var promised_proposalID:ProposalID = null
   private var last_accepted_id:ProposalID = null
   private var learned_proposals:Map[String,(Serializable,ProposalID, Int)] = Map()
   private var highest_proposalID:ProposalID = null
   private var last_accepted_value:Serializable = null      
   private var proposal_value:Serializable = null   
   private var value:Serializable = null
   private val quorum_size = pm.size/2 + 1
   private var accepteds:List[(Serializable,ProposalID)] = List()
   private var promises:Map[String,List[(Serializable,ProposalID)]]=Map()
   private var promises_received = 0
   private var accepts:Set[Int] = Set()
   private var socket:ActorRef = null
   private var lock1 = new Object()
   
   this.read_from_disk()
      
   IO(Udp) ! Udp.Bind(self, pm(this.id))
 
  def receive = {
    case Udp.Bound(local) =>  {
      socket = sender
      context.become(ready())
    }
  }
  
  def ready(): Receive = {
    case Udp.Received(data, remote) =>{
      
      Util.toObject(data) match{   
        
        case receive_prepare(pid) =>{
          logger.info("receive prepare{proposal_id:"+pid.toString()+"} from remote:["+remote.toString()+"]")
          this.update_highest_proposalID(pid)              
          var org = if(this.promised_proposalID == null ) "null" else this.promised_proposalID.toString()
          logger.info("Original promised_id:["+org+"],received prepare_id:["+pid.toString()+"]")
          if(promised_proposalID == null || !promised_proposalID.isGreater(pid)){
             promised_proposalID = pid
             logger.info("Send back promise to remote:["+remote.toString()+"]")
             socket ! Udp.Send(Util.toByteString(receive_promise(this.id,this.last_accepted_value,this.last_accepted_id,pid)),remote)
          }else{
              logger.info("Send back nack to remote:["+remote.toString()+"]")
              socket ! Udp.Send(Util.toByteString(receive_nack(this.highest_proposalID)),remote)              
          }     
        }
        
        case receive_promise(nid, accepted_value, accepted_id, promised_pid) => {

          var key = promised_pid.toString()
          var l = List((accepted_value,accepted_id))
          if(this.promises.contains(key))
            l = l++promises(key)
          this.promises+=(key->l)
          if(l.size >= this.quorum_size){
            var mid:ProposalID = null
            l.foreach(f =>{
             if(f._2 !=null && (mid == null || f._2.isGreater(mid))){
              mid = f._2
              this.proposal_value = f._1
              if(this.proposal_id.isSmaller(promised_pid))
                this.proposal_id = promised_pid
              }
            }
            )           
            this.promises = Map()
            this.send_accept()              
          }
          
        }
        
        case receive_nack(higher_pid) => {
          logger.info("Receive nack from:"+remote.toString())
          this.update_highest_proposalID(higher_pid)
          if(this.proposal_id != null && higher_pid.isGreater(this.proposal_id)){
            socket ! Udp.Send(Util.toByteString(propose_cmd(this.proposal_value)), pm(this.id)) 
          }           
        }
        
        case accept(acc_v, acc_pid) =>{

          this.update_highest_proposalID(acc_pid)
          logger.info("Receive accept from:"+remote.toString()+" value:["+acc_v+"],proposal id:["+acc_pid.toString()+"]")
          if(!this.promised_proposalID.isGreater(acc_pid)){              
            this.last_accepted_value = acc_v
            this.last_accepted_id = acc_pid
            this.promised_proposalID = acc_pid
            socket ! Udp.Send(Util.toByteString(accepted( this.id, this.last_accepted_value,this.last_accepted_id)),remote)
            this.write_to_disk()
            //notify_learners()
           }else{
            logger.info("send back nack to remote:["+remote.toString()+"]")
            socket ! Udp.Send(Util.toByteString(receive_nack(highest_proposalID)),remote)
          }
          
        }
        
        case accepted(nid, acc_v, acc_pid) =>{
          
          logger.info("learning a value:["+acc_v+"] from node "+nid)
          var key = acc_pid.toString()           
          if(!this.learned_proposals.contains(key))
            this.learned_proposals+=(key->(acc_v,acc_pid,1))
          else{
            var temp1 = this.learned_proposals(key)
            var temp2 = temp1._3+1
            this.learned_proposals+=(key->(acc_v,acc_pid,temp2))
            if(temp2 >= this.quorum_size){
              this.value = acc_v
              this.learned_proposals = Map()
              print("\nLearned value:"+acc_v+"\n->")
            }
          }            
                 
        }      
       

       
       case propose_cmd(v) =>{
         
        logger.info("receive propose command:propose{value:"+v+"}")
        if(this.proposal_value == null)
          this.proposal_value = v
        this.lock1.synchronized{
          if(this.highest_proposalID !=null){
            this.proposal_id= new ProposalID(this.highest_proposalID.getNumber+1, this.id)  
            this.update_highest_proposalID(this.proposal_id)
          } 
          else
            this.proposal_id = new ProposalID(0, this.id) 
          logger.info("set propose_id:"+this.proposal_id.toString())
          for((k,v)<-pm){
            socket ! Udp.Send(Util.toByteString(receive_prepare(proposal_id)), v)
            logger.info("Send prepare{proposal_id:"+this.proposal_id.toString()+ "} to node:"+k)
          }                  
        }
        
       }
       
     }
    }
    
    
    case propose(v:Serializable, nid:Int) => {
      if(nid <= pm.size){
        socket ! Udp.Send(Util.toByteString(propose_cmd(v)), pm(nid))
        logger.info("ask node:["+nid+"] to popose {value:"+v+"}")
      }
    }
          
    case Udp.Unbind  => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)   
    
  }

  
  def update_highest_proposalID(other:ProposalID){ 
    if(this.highest_proposalID==null||(other!=null&&this.highest_proposalID.isSmaller(other)))
      this.highest_proposalID = other
  }
  
    
  def notify_learners(){
    logger.info("notify all learners ")
    pm.values.foreach(f=> socket ! Udp.Send(Util.toByteString(accepted( this.id, this.last_accepted_value,this.last_accepted_id)),f))     
  }

  
  def send_accept(){
    if(this.proposal_id != null && this.proposal_value != null){
      for((k, v) <- pm){
        logger.info("send accept{proposal_id:"+this.proposal_id.toString()+", proposal_value:"+
          this.proposal_value+"} to node:"+k)
          socket ! Udp.Send(Util.toByteString(accept(proposal_value,this.proposal_id)),v)
      }
    }     
  }

  
  def read_from_disk(){
    
    try{
      var temp = Source.fromFile("/tmp/Node_"+this.id+".txt").getLines().toArray
      if(temp.size != 0){
        var temp1 = temp(0).split(" ", 3)
        if(temp1.size >= 3){
          var n1 = temp1(0).trim().toInt
          var n2 = temp1(1).trim().toInt
          var v = temp1(2).trim()
          this.last_accepted_id = new ProposalID(n1, n2)
          this.last_accepted_value = v
        }
      }
    }
    catch{
      case e:Exception => {}
    }
    
  }
  
  def write_to_disk(){
    val out = new FileWriter("/tmp/Node_"+this.id+".txt")
    if(this.last_accepted_id != null){
      var temp = this.last_accepted_id.getNumber+" "+this.last_accepted_id.getPid+" "+this.last_accepted_value
      out.write(temp)
    }
    out.close()   
  }
  
}