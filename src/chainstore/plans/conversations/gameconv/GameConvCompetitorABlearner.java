package chainstore.plans.conversations.gameconv;

import java.util.Iterator;
import java.util.TreeMap;
import java.util.UUID;
import java.util.ArrayList;

import chainstore.CompetitorABlearnerDataModel;
import chainstore.plans.conversations.gameconv.CptrActRequest;
import chainstore.plans.conversations.gameconv.CptrActResponse;
import chainstore.plans.conversations.gameconv.ResultInform;
//import convexample.conversations.cfp.AcceptMsg;
//import convexample.conversations.cfp.RejectMsg;

import presage.Message;
// import presage;
import presage.Input;
import presage.Signal;
import presage.abstractparticipant.Interpreter;
//import presage.abstractparticipant.plan.ConversationMulticast;
//import presage.abstractparticipant.plan.ConversationMulticastFSM;
import presage.abstractparticipant.plan.ConversationSingleFSM;
import presage.abstractparticipant.plan.ConversationSingle;
import presage.abstractparticipant.plan.FSM;
import presage.abstractparticipant.plan.Plan;
import presage.abstractparticipant.plan.State;
import presage.abstractparticipant.plan.Transition;

public class GameConvCompetitorABlearner extends ConversationSingleFSM {

	CompetitorABlearnerDataModel dm;
	
	int strategyPlayed;
	
	// Can be or Compliant == 0 or Non Compliant == 1   // Note these are action id's not payoffs we can extend to not play etc with 3/4/5 etc
	// int myStrategy;
	
	// ArrayList<CptrActResponse> bids = new ArrayList<CptrActResponse>();
	
	public GameConvCompetitorABlearner(CompetitorABlearnerDataModel dm, Interpreter interpreter, String myKey, String theirId, String theirKey) {
		super(dm, interpreter, myKey, "cfp", theirId, theirKey);
		// TODO Auto-generated constructor stub
		this.updateTimeout(Plan.NO_TIMEOUT);
		this.dm = dm;
	}

	@Override
	public void handleAction(Transition trs, Input input) {
		// TODO Auto-generated method stub

		if (trs.getAction().equalsIgnoreCase(FSM.NO_ACTION)){
			return;
		} else if (trs.getAction().equalsIgnoreCase("chooseresponse")){
			chooseresponse(input);			
		} else if (trs.getAction().equalsIgnoreCase("evaluateresult")){
			evaluateresult(input);
		}else if (trs.getAction().equalsIgnoreCase("error")){
			error(input);
		}
	}
	
	public void chooseresponse(Input input){
		
		System.out.println("Choosing Strategy to Play"); 
		// System.out.println(mapAsString(to_toKey));

		CptrActRequest car = (CptrActRequest)input;

		// dm.myStrategy = 1;
		
		// if a random between 0 - 0.99 is less than P(NC) the play NC = 1
		if (dm.random.nextDouble() < dm.p_nc){ // NC
			dm.myEnvironment.act( new CptrActResponse(car.getFrom(), dm.myId, car.getFromKey(), this.myKey, this.type, dm.getTime(), 1), dm.myId,dm.environmentAuthCode);
			strategyPlayed = 1; 
		} else {	// C
			dm.myEnvironment.act( new CptrActResponse(car.getFrom(), dm.myId, car.getFromKey(), this.myKey, this.type, dm.getTime(), 0), dm.myId,dm.environmentAuthCode);
			strategyPlayed = 0; 
		}
		
		System.out.println("Sending response to " + car.getFrom());
		
	}
	
	public void evaluateresult(Input input){
		
		System.out.println("Evaluating the game result"); 
		
		ResultInform ri = (ResultInform)input;

		int authorStrat = ri.getAuthorActions().get(ri.getAuthorActions().size()-1);
		
		if (authorStrat == 0){ // if they played Defensive the agent becomes more compliant
			dm.p_nc = dm.p_nc - dm.alpha*dm.p_nc;
		} else { // They played Passive so we become more NC
			dm.p_nc = dm.p_nc + dm.beta*(1-dm.p_nc);
		}
		
		// int bid = dm.getBidprice();
		
		// dm.myEnvironment.act( new CptrActResponse(cfp.getFrom(), dm.myId, cfp.getFromKey(), this.myKey, 
		// 		"bid", this.type, dm.getTime(), bid), dm.myId, dm.environmentAuthCode);
		
		// System.out.println("Sent bid of " + bid + " to " + cfp.getFrom());
		
		//TODO need to signal back to self that a bid was sent to do the state transistion.
		
		// interpreter.addInput(new Signal("sent_bid", this.getMyKey(),dm.getTime()));
	}
	
	public void error(Input input){
		
		System.err.println("FSM error State!!!");
	}
	
	
	public void causehalt(Input input){
		
		System.err.println("Time Out Occured!!!");
		
		while(true){
			try{
			Thread.sleep(2000);
			} catch (Exception e){
			}
		}
		
	}
	
	
	@Override
	public FSM initialiseFSM() {
//		 You can do this or serialise from an xml file 

		FSM fsm = new FSM("cfp");

		State initial_state;
		State awaiting_result;

		try {
			initial_state = new State("initial_state");
			// initial_state.addTransition(new Transition(FSM.INITIATE, "sendcfp", "awaiting_bids", 5));
			initial_state.addTransition(new Transition("CptrActRequest", "chooseresponse", "awaiting_result", 10));
			
		} catch (Exception e){
			System.err.println("Error in State generation 0" + e);
			initial_state = new State("failed");
		}

		
		try {
			awaiting_result = new State("awaiting_result");
			// If we get a time out we want to halt as timeouts shouldn't occur in this scenario
			awaiting_result.addTransition(new Transition(Plan.TIME_OUT, "causehalt", FSM.END_STATE.getStatename(), Transition.NO_TIMEOUT));
			// We should get back the result of the game
			awaiting_result.addTransition(new Transition("ResultInform", "evaluateresult", FSM.END_STATE.getStatename(), Transition.NO_TIMEOUT));
			
		} catch (Exception e){
			System.err.println("Error in State generation 1" + e);
			awaiting_result = new State("failed");
		}
		
		fsm.setCurrentState(initial_state);

		try{	
			fsm.addState(initial_state);
			fsm.addState(awaiting_result);

		} catch (Exception e){
			System.err.println("Error in fsm generation " + e);			
		}

		return fsm;
	}

//	@Override
//	public ConversationSingle spawn(String myKey, String theirId, String theirKey) {
//		// TODO Auto-generated method stub
//		return new GameConvCompetitor(dm, interpreter, myKey, theirId, theirKey);
//	}

	@Override
	public ConversationSingle spawn(String myKey, Message msg) {
		// TODO Auto-generated method stub
		
		if (msg == null)
			return new GameConvCompetitorABlearner(dm, interpreter, myKey, null, null);
		
		return new GameConvCompetitorABlearner(dm, interpreter, myKey, msg.getFrom(), msg.getFromKey());
	}
	
	@Override
	public void print() {
		// TODO Auto-generated method stub

	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return "ChainGame: <" + this.theirId + "," + this.theirKey + ">," + this.myKey + ", " + this.getType() + ", " + this.fsm.getCurrentState().getStatename()+ ", "+  this.getTimeOut();
	}

	@Override
	public boolean inhibits(Plan ihandler) {
		// TODO Auto-generated method stub
		return false;
	}

	public String mapAsString(TreeMap<String, String> map){
		String result = "";
		
		if (map == null)
			return null;
		
		Iterator<String> iterator = map.keySet().iterator();
		while (iterator.hasNext()){
			String id = iterator.next();
				result += "<" + id  +  map.get(id) + ">";
		}
		
		if (result.equals(""))
			return "";
		
		return result;
	}
	
	
}
