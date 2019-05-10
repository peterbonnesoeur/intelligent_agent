package template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Set;

import java.util.*;




/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;


/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	/* Define the type of alghorithm */
	enum Algorithm { BFS, ASTAR }
	/* The type of heuristic we will use */
	enum Method {NO_METHOD, MAX_DISTANCE, AVR_CUMUL_DIST}
	/* the state of our tasks */
	enum Package {NOT_PICKED, PICKED, DELIVERED}
	
	/* Environment */
	Topology topology;
	List<City> cities;
	TaskSet carriedTask = null;
	ArrayList <Task> taskMap;
	TaskDistribution td;
	Vehicle vehicle;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;
	int generalID;
	int changeID = -1;
/* 	HashMap<Integer,State> id_2_state = new HashMap<Integer,State>();
 */
	/* the planning class */
	Algorithm algorithm;
	

	State initialState;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.generalID = 0;
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		this.cities=this.topology.cities();

		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		// ...
	}
	
	@Override
	public Plan plan(Vehicle myvehicle, TaskSet tasks) {
		
		Plan plan = null;
		Method	myMethod = Method.MAX_DISTANCE;
		vehicle = myvehicle;
		// Compute the plan with the selected algorithm.
		
		initialize(vehicle,tasks,myMethod);
	
		// set a timer to know how much time the algorithm take
		double timer = System.currentTimeMillis(); 
		
		switch (algorithm) {
		case ASTAR:
			// ...
			//myMethod = Method.MAX_DISTANCE;
			plan = ASTAR(myMethod);
			break;
		case BFS:
			// ...
			//plan = BFSPlan();
			plan = BFSPlan();
			break;
		default:
			 
		}	


		System.out.println(tasks.size()+"tasks in "+ plan.totalDistance()+ "km for a duration of "+ (System.currentTimeMillis()-timer) + "milliseconds");
		
		return plan;
	}

	
	
	private void initialize(Vehicle vehicle, TaskSet tasks, Method method ) {
		
		int   initialWeight=0 ,i = 0;
		Package[] initialTaskStatus;
		
		/* Do the initialisation with the previous state of the algorithm in case a task is not available anymore*/

		this.taskMap = new ArrayList<Task>() ;
		
		/* Case 1 : simulations with multiple agents */
		if(carriedTask!=null)
		{	
			/*initialize the taskStatus register with the previous carried tasks*/
			initialTaskStatus = new Package[carriedTask.size()+tasks.size()];
			for(Task remains:carriedTask)
			{
				initialTaskStatus[i]= Package.PICKED;
				this.taskMap.add(remains);
				initialWeight +=remains.weight;
				i++;
			}
		}
		else 		/* Case 2 : simulation with only one agent*/
			initialTaskStatus = new Package[tasks.size()];

		for (Task t:tasks)
		{
			initialTaskStatus[i]=Package.NOT_PICKED;
			this.taskMap.add(t);
			i++;
		}
		
		initialState =  new State(vehicle.getCurrentCity(),initialTaskStatus,(State)null,0.0,initialWeight,method);
	}



	private class State extends ArrayList<Object> {
	
		/* State parameters */	
		int id;
		public City myCity;
		Package[] taskStatus;

		/* link to the previous state of our simulation */
		public State pState;

		/* tasks parameters */
		public int weight=0;
		public int nbDelivered=0;

	

		/* total distance traveled by our agent */
		public double travelCost;

		/* Heuristic that will help us to find an optimal path in the A* algorithm */
		public double heuristic=0.0;
		
		
		public State(City cCity, Package[] tStatus, State previousState, double cost, int weight, Method methodToUse) {
			
			this.id = generalID++;
			this.myCity=cCity;
			this.taskStatus=tStatus;
			this.pState=previousState;
			this.travelCost=cost;
			this.weight=weight;
			
			computeHeuristic (methodToUse);
			//id_2_state.put(this.id, this);
		}
		
		private void computeHeuristic(Method myMethod) {	
			
			switch (myMethod) {

				case MAX_DISTANCE:
				{
					this.nbDelivered = 0;
					double maxDistance=0;
					double taskDistance=0;
					for (int i = 0; i < taskStatus.length; i++)
					{

						/* Gives us the minimum distance to one of the tasks*/

						if(taskStatus[i]==Package.NOT_PICKED)
						{
							taskDistance = myCity.distanceTo(taskMap.get(i).pickupCity) + 
							cities.get(taskMap.get(i).pickupCity.id).distanceTo(taskMap.get(i).deliveryCity);
						}
						else if(taskStatus[i]==Package.PICKED)
						{
							taskDistance = myCity.distanceTo(taskMap.get(i).pickupCity);
						}
						else if(taskStatus[i]==Package.DELIVERED)
						{
							this.nbDelivered++;
						}
						
						if(taskDistance>maxDistance)
						{
							maxDistance = taskDistance;							
						}
					}
					this.heuristic=maxDistance;	
					break;													
				}
				case AVR_CUMUL_DIST:
				{
					double totalDist = 0;

					/* Give us the mean distance to our tasks */

					for (int i = 0; i < taskStatus.length; i++)
					{
						switch (taskStatus[i]) {
						case  NOT_PICKED :
							totalDist += myCity.distanceTo(taskMap.get(i).pickupCity) + 
							cities.get(taskMap.get(i).pickupCity.id).distanceTo(taskMap.get(i).deliveryCity);
						break;
						
						case  PICKED :
							totalDist += myCity.distanceTo(taskMap.get(i).pickupCity);
						break;
						
						case  DELIVERED :
							nbDelivered++;
						break;
						}
					}
					this.heuristic=totalDist/taskStatus.length;	
					break;		
				}	
				case NO_METHOD:
				{
					for (int i = 0; i < taskStatus.length; i++)
					{
						if(taskStatus[i]== Package.DELIVERED) 
						{
							nbDelivered++;
						}
					}
				}	
				break;
			}	
		}
			
	
			
		public Plan planGenerator() {

			/* Gives us the course of actions that will lead us to the current state  */
			
			ArrayList<State> planning = new ArrayList<State>();
		
			State myState = this;
			
			planning.add(0,myState);

			while(myState.pState != null) {
				myState = myState.pState;
				planning.add(0,myState);
			}
			Plan myPlan=new Plan( ((State)planning.get(0)).myCity);

				for (int i =1; i< planning.size();i++)
				{
					myState = (State) planning.get(i-1);
					State nextState = (State) planning.get(i);
					if (myState.myCity != nextState.myCity ) {
						for (City path : myState.myCity.pathTo(nextState.myCity)) 
						{	
							myPlan.appendMove(path);
						}
					}	
					
					for (int j = 0;j<myState.taskStatus.length;j++) {
						if (myState.taskStatus[j] != nextState.taskStatus[j]) {
							
							if (myState.taskStatus[j] == Package.NOT_PICKED) 
								myPlan.appendPickup(taskMap.get(j));
							else if(myState.taskStatus[j] == Package.PICKED)
								myPlan.appendDelivery(taskMap.get(j));
						}
					}	
				}
				return myPlan;
			}
			

		  /*

		  								DESCENDANT

		  */



		private ArrayList<State>  descendant(Method methodToUse){

			// Gives us the next possible States from our current state
		
			ArrayList<State> descendantList = new ArrayList<State>();
			
			int deliv=0;

			for (int i = 0 ;i<taskStatus.length;i++) 
			{	
				if (taskStatus[i] == Package.PICKED || ( taskStatus[i] == Package.NOT_PICKED &&  ( this.weight + taskMap.get(i).weight ) < vehicle.capacity()) ) {
					
					Package[] nextTaskStatus = Arrays.copyOf(taskStatus,taskStatus.length);
					nextTaskStatus [i]= (taskStatus[i]== Package.PICKED ) ? Package.DELIVERED :Package.PICKED ;
					int nextWeight = this.weight ;
					City nextCity;
					
					if (taskStatus[i]== Package.NOT_PICKED) 
					{
						nextWeight += taskMap.get(i).weight;
						nextCity = taskMap.get(i).pickupCity;
					}else 
						nextCity = taskMap.get(i).deliveryCity;
					
						
					descendantList.add(new State(nextCity,nextTaskStatus,this,this.travelCost+this.myCity.distanceTo(nextCity),nextWeight,methodToUse));

				} else if (taskStatus[i] == Package.DELIVERED)
						deliv++;
			}
			return descendantList;
		}
	}
	
	



	private boolean nEqualState(ArrayList<State> memory, State ourState){
		
		if (memory == null)
			return false;

		State comparator = null;	
		int i =-1;	
		while (!memory.isEmpty()){
			comparator = memory.remove(0);
			i++;
			if (comparator.myCity == ourState.myCity){
				if(Arrays.equals(comparator.taskStatus,ourState.taskStatus)){
					if (ourState.travelCost<comparator.travelCost){
						//changeID = i;
						return true;
					}
					else
						return false;
				}																				
			}									
		}
		return true;
	}


	private Plan BFSPlan() {
		ArrayList<State> memory = new ArrayList<State>();
		ArrayList<State> listState = new ArrayList<State>();
		listState.add(initialState);
		
		State finalNode = null;
		double totalCost = Double.POSITIVE_INFINITY;
		
		int compteur=0;


		while(!listState.isEmpty()) {
			State a = (State)listState.remove(0);
			

			if (nEqualState( memory, a))
			{	
				compteur++;
				
				if(changeID>0){
					memory.set(changeID,a);
					changeID = -1;					
				}				
				
				if (a.nbDelivered == taskMap.size() && a.travelCost<totalCost) {
					
					totalCost = a.travelCost;
					finalNode = a;	
				}
				else {
					listState.addAll (a.descendant(Method.NO_METHOD));
				}	
			}
			memory.add((State)a);
		}

		System.out.println(compteur);
		Plan myPlan;
		
		if(finalNode ==null)
			return (myPlan = Plan.EMPTY);
		else{
			return finalNode.planGenerator();
		}
	}

	
	/*

								Astar

	*/
	
	private Plan ASTAR(Method myMethod){

		System.out.println("we are in ASTAR");

		TreeMap<Double, State> scoreMap = new TreeMap<Double,State>();

		HashMap<Double, Integer> score_2_id = new HashMap<Double, Integer>();

		scoreMap.put(initialState.heuristic+initialState.travelCost,initialState);

		State finalState = null;
		double totalCost = Double.POSITIVE_INFINITY;
		
		int iter=0;
		State head = null;
		 while(true){
			iter++;
			head = scoreMap.get(scoreMap.firstKey());
			scoreMap.remove(scoreMap.firstKey());

			if(!score_2_id.containsValue(head.id)) 
			{
				for(State s : head.descendant(myMethod))
				{
					scoreMap.put(s.heuristic+s.travelCost,s);
				}	
				if (head.nbDelivered == taskMap.size()) {	
					totalCost = head.travelCost+head.heuristic;
					finalState = head;
					break;	
				}
			}
			score_2_id.put(head.heuristic+head.travelCost,head.id);
			
					
		}				
		
		System.out.println(iter);
		Plan myPlan;
		State s = finalState;
		while(s.pState.id!=0)
		{
			s=s.pState;
		}
		
		if(finalState ==null)
			return (myPlan = Plan.EMPTY);
		else{
			return finalState.planGenerator();
		}
	} 
 

	//******************************************/
	
	@Override
	public void planCancelled(TaskSet carriedTasks) {
		// In case a task is no longer available
		if (!carriedTasks.isEmpty()) 
			this.carriedTask = carriedTasks;
		
	}
	
}
