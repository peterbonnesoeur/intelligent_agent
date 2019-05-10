package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */

@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private Random rand;
    private List<Vehicle> vehicles;
    private TaskSet tasks;
    private int stopCondition = 10000;
    
    enum Status { PICKUP,DELIVER}
    
    
    private HashMap<Group,Vehicle> group2Vehicle = new HashMap<Group,Vehicle>();
    
    enum Algorithm { NAIVE, TYPE1, TYPE2 }
    
    Algorithm algorithm;
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
    	
    	
    	String algorithmName = agent.readProperty("algorithm", String.class, "NAIVE");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		System.out.println("new Agent " );
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
		this.agent = agent;
		this.rand = new Random();
		
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
    	
    	this.tasks = tasks;
    	this.vehicles = vehicles;
    	
        long time_start = System.currentTimeMillis();
        
		
		
		Plan planVehicle1= null;
		List<Plan> plans = new ArrayList<Plan>();
		
		switch (algorithm) {
			case NAIVE : 
				planVehicle1 = naivePlan(vehicles.get(0), tasks);
				plans.add(planVehicle1);  
			    while (plans.size() < vehicles.size()) {
			        plans.add(Plan.EMPTY);
			    }
				break;
			case TYPE1: 
				plans = stockasticLocalSearch(vehicles,tasks);
				break;
				
			case TYPE2:
				plans = steadyStateGeneticAlg(vehicles,tasks);
				break;
			default:
				throw new AssertionError("Should not happen.");
		}
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
    
    private List<Plan> stockasticLocalSearch(List<Vehicle> vehicles, TaskSet tasks) {
    	
		int nbNeighboors = 20;
	int nbIter = 10000;
	Coordination bestCoord = new Coordination(vehicles,tasks);
	ArrayList<Coordination> neighboors = new ArrayList<Coordination>();
	ArrayList<Double> costs = new ArrayList<Double>();
	double probLimit = 0.5;
	
	System.out.println("we had a initial cost of "+bestCoord.getTotalCost());
	
	for(int i=0;i<nbIter;i++)
	{
		System.out.println("Iter: "+i);
		neighboors.clear();
		costs.clear();
		neighboors.add(bestCoord);
		costs.add(bestCoord.getTotalCost());
		//System.out.println("we add the current best");
		for(int j=0;j<nbNeighboors;j++)
		{
			
			Coordination mutant = bestCoord.mutate();
			neighboors.add(mutant);
			costs.add(mutant.getTotalCost());
		}
		//System.out.println("we check if we go in the if");
		if(rand.nextDouble()>probLimit)
		{
			//System.out.println("we change the the best");
			int bestIndex = 0;
			for(int j=0;j<neighboors.size();j++)
			{
				if(costs.get(j)<costs.get(bestIndex))
				{
					bestIndex=j;
				}
				
			}
			bestCoord = neighboors.get(bestIndex);
			//bestCoord = neighboors.get(costs.indexOf(Collections.min(costs)));
		}
		//System.out.println("the current best cost is "+bestCoord.getTotalCost());
		
	}

	//bestCoord.printCoord();

	System.out.println("a final cost of "+bestCoord.getTotalCost());
	List<Plan> plan = bestCoord.getPlans();
	
	return plan;
}

private List<Plan> steadyStateGeneticAlg(List<Vehicle> vehicles, TaskSet tasks) {
	
	int populationSize = 200;
	int nbGen = 300;
	
	Map<Coordination,Double> populationProb = new HashMap<Coordination,Double>();
	Map<Coordination,Double> populationCost = new HashMap<Coordination,Double>();
	
	
	
	for(int i=0;i<populationSize;i++)
	{
		Coordination ind = new Coordination(vehicles,tasks);
		populationProb.put(ind,1/ind.getTotalCost());
		populationCost.put(ind,ind.getTotalCost());
	}
	
	Distribution<Coordination> mutateDist = new Distribution<Coordination>(populationProb);
	
	for(int i=0;i<nbGen;i++)
	{
		for(int j=0;j<populationSize;j++)
		{
			Coordination bestCoord = getBestInd(populationCost, mutateDist);
			//System.out.println("the current best is "+bestCoord.getTotalCost());
			Coordination parent = mutateDist.sample();
			Coordination mutant = parent.mutate();
			
			
			if(parent.getTotalCost()>mutant.getTotalCost())
			{
				Coordination toDie = getWorstInd(populationCost, mutateDist);
				populationProb.put(mutant, 1/mutant.getTotalCost());
				populationProb.remove(toDie);
				
				populationCost.put(mutant, mutant.getTotalCost());
				populationCost.remove(toDie);
			}
			mutateDist = new Distribution<Coordination>(populationProb);
			
		}
	}
	Coordination bestCoord = getBestInd(populationCost, mutateDist);
	
	List<Plan> plan = bestCoord.getPlans();
	System.out.println("a final cost of "+bestCoord.getTotalCost());
	return plan;
}

private Coordination getBestInd(Map<Coordination,Double> populationCost, 
		Distribution<Coordination> mutateDist) 
{
		Coordination bestCoord = mutateDist.sample();
	for(Coordination coord:populationCost.keySet())
	{
		if(coord.getTotalCost()<bestCoord.getTotalCost())
		{
			bestCoord = coord;
		}
	}
	return bestCoord;
}
	
private Coordination getWorstInd(Map<Coordination,Double> populationCost, 
		Distribution<Coordination> mutateDist) 
{
		Coordination worstCoord = mutateDist.sample();
	for(Coordination coord:populationCost.keySet())
	{
		if(coord.getTotalCost()>worstCoord.getTotalCost())
		{
			worstCoord = coord;
		}
	}
	return worstCoord;
}

class Distribution<T>{
    List<Double> probs = new ArrayList<Double>();
    List<T> events = new ArrayList<T>();
    double sumProb;

    Distribution(Map<T,Double> probs){
        for(T event : probs.keySet()){
            sumProb += probs.get(event);
            events.add(event);
            this.probs.add(probs.get(event));
        }
    }

    public T sample(){
        T value;
        double prob = rand.nextDouble()*sumProb;
        int i;
        for(i=0; prob>0; i++){
            prob-= probs.get(i);
        }
        return events.get(i-1);
    }
}
    
    
    
    
    
    private class Coordination{
    		
    		private HashMap<Vehicle,Sequence> sequences = new HashMap<Vehicle,Sequence>();
    		
    		public Coordination(Coordination coord) 
    		{
    			for (Vehicle v :vehicles) {
    				 Sequence mySequence = new Sequence (coord.getSequences().get(v));
    				sequences.put(v,mySequence);
    			}
    		}
    		
    		public Coordination(List<Vehicle> myVehicles, TaskSet myTasks) {
    			
    			int nbTasks = 0;
    			TaskSet TaskLeft = null;
    			TaskLeft = TaskSet.copyOf(myTasks);
    			System.out.println("we are creating coordination for "+myVehicles.size()+" vehicles with "
    			+ TaskLeft.size()+" tasks");
    			
    			ArrayList<ArrayList<Task>> ArrayTasks = new ArrayList<ArrayList<Task>>();
    			
    			for(Vehicle V:myVehicles)
    			{
    				ArrayTasks.add(new ArrayList<Task>());
    			}
    			
    			
    			int selectedV = 0;
    			for(Task task:TaskLeft) 
    			{
    				selectedV = rand.nextInt(myVehicles.size());
    				
    				ArrayTasks.get(selectedV).add(task);
    			}
    			int iterS=0;
    			for(Vehicle V:myVehicles)
    			{
    				sequences.put(V, new Sequence(ArrayTasks.get(iterS),V));
    				iterS++;
    			}
    			
    		}
    		
    		public Coordination mutate() {
    			Coordination mut1 = new Coordination(this);
    			double gamma = 0.5;
    			if(rand.nextDouble()>gamma)
    			{
    				mut1.changeSeqVehicle();
    			}
    			for(int i=0; i< vehicles.size();i++)
    			{
    				if(rand.nextDouble()>0.5*gamma)
        			{
        				mut1.changeGroupSequence();
        			}
    				if(rand.nextDouble()>1*gamma)
        			{
    					mut1.sequences.get(vehicles.get(i)).changeGroupOrder();
        			}
    				
    				if(rand.nextDouble()>1*gamma)
        			{
    					mut1.sequences.get(vehicles.get(i)).changeTaskGroup();
        			}
    				if(rand.nextDouble()>1*gamma)
        			{
    					mut1.sequences.get(vehicles.get(i)).changeTaskGroup();
        			}
    				if(rand.nextDouble()>1*gamma)
        			{
    					mut1.sequences.get(vehicles.get(i)).sequenceChangeTaskOrder();
    				}
    				if(rand.nextDouble()>1*gamma)
        			{
    					mut1.sequences.get(vehicles.get(i)).sequenceChangeTaskOrder();
    				}
    			}
    			//System.out.println("Total cost after mutation" + this.getTotalCost());
    			//this.printCoord();
    			//System.out.println("Total cost of the mutation after" + mut1.getTotalCost());
    			//this.printCoord();

    			mut1.getTotalCost();
    			return mut1;
    		}
    		
    		private void changeSeqVehicle(){
    			if(vehicles.size()>1)
    			{
    				Vehicle V1 = vehicles.get(rand.nextInt(vehicles.size()));
    				Vehicle V2 = null;
    				do {
    					V2 = vehicles.get(rand.nextInt(vehicles.size()));
    				}while(V2==V1);
    				Sequence tempSeq = new Sequence(sequences.get(V1));
    				if(V2.capacity()>=tempSeq.getMaxWeight())
    				{
    					tempSeq.setVehicle(V2);
    					sequences.put(V1,sequences.get(V2));
    					sequences.get(V1).setVehicle(V1);
    					sequences.put(V2,tempSeq);
    					updateGroup2Vehicle();
    				}
    			}
    		}
    		
    		private void changeGroupSequence() {
    			if(vehicles.size()>1)
    			{
    				Vehicle V1, V2 = null;
    				
    				do 
    				{
    					V1 = vehicles.get(rand.nextInt(vehicles.size()));
    				}
    				while(sequences.get(V1).getGroups().size()==0);
    				
    				do 
    				{
    					V2 = vehicles.get(rand.nextInt(vehicles.size()));
    				}
    				while(V1==V2);
    				
    				sequences.get(V2).addGroup(sequences.get(V1).getGroups().remove(rand.nextInt(sequences.get(V1).getGroups().size())));
    				
    				sequences.get(V1).updateSeqCost();
    				sequences.get(V2).updateSeqCost();
    			}
    		}
    		public HashMap<Vehicle,Sequence> getSequences()
    		{
    			return sequences;
    		}
    		public List<Plan> getPlans(){
    			updateGroup2Vehicle();
    			List<Plan> plans = new ArrayList<Plan>();
    			for(Vehicle V : vehicles) {
    				plans.add(sequences.get(V).planGenerator());
    			}
    			return plans;
    		}
    		public double getTotalCost() {
    			double totalCost=0;
    			for(Vehicle V:vehicles)
    			{
    				totalCost+=sequences.get(V).updateSeqCost();
    			}
    			return totalCost;
    		}
    		
    		public void updateGroup2Vehicle() {
    			group2Vehicle=new HashMap<Group,Vehicle>();
    			for(Vehicle V:vehicles)
    			{
    				sequences.get(V).updateGroup2Vehicle();
    			}
    		}
    		public void printCoord() {
    			for(Vehicle V:vehicles) 
    			{
    				System.out.println("pour le vehicle "+ V.id() +" la sequence a "+sequences.get(V).getSeqVehicle().id());
    				sequences.get(V).printGroups();
    			}
    		}
    }
    private class Sequence{
    		
	    	private ArrayList<Group> groups = new ArrayList<Group>();
	    	private Vehicle seqVehicle=null;
	    	private double seqCost = 0;
	    	
	    	public Sequence(ArrayList<Task> seqTasks,Vehicle currentVehicle) {
	    		
	    		//System.out.println("we are creating a sequence of "+seqTasks.size()+" groups" );
	    		
	    		this.seqVehicle = currentVehicle;
	    		
	    		if(seqTasks!=null)
	    		{
	    			
	    			for(int i=0;i<seqTasks.size();i++)
	    			{
	    				//might have to check the equalities in the objects instantiation.
	    				Group currentGroup = new Group(new ArrayList<Task>(), new ArrayList<Event>());	///////////////////////UGLY						
	    				this.groups.add(currentGroup);
	    				
	    				//not sure the hash key will update groups(probably yes)
	    				
	    				group2Vehicle.put(currentGroup, currentVehicle);
	    				
	    			}
	    			for(Task task:seqTasks) 
	    			{
	    				
	    				int i=0;
	    				do {
	    				i = rand.nextInt((this.groups.size()));
	    				}while(!this.groups.get(i).addTask(task));
	    				
	    			}
	    			
	    			
	    		}
	    	}
	    	public void changeTaskGroup() {
	    		//updateSequenceEvent();
	    		int taskNumber = 0;
	    		for (int i = 0 ;i<groups.size();i++) {
	    			taskNumber += groups.get(i).getGroupTasks().size();
	    		}
	    		
	    		if(groups.size()>1 && taskNumber>1)
	    		{
	    			//System.out.println("in task group");
	    			int index =0;
	    			//printGroups();
	    			for (int i = 0; i<groups.size();i++)
		    		//System.out.println("tasks : " + groups.get(i).getGroupTasks());
	    			
	    			
	    			do {
	    				index =rand.nextInt(groups.size());
	    				
	    			}while(groups.get(index).getGroupTasks().size()==0);
	    			//System.out.println(groups.get(index).getGroupTasks().size());
	    			
	    			
	    			
	    		//	System.out.println("REMOVEEEEEEE");
	    			Task myTask = groups.get(index).removeTask(rand.nextInt(groups.get(index).getGroupTasks().size()));
	    			
	    			//System.out.println("Middle chANge group");
	    			
	    			int newIndex = index;
	    			do {
	    			 newIndex = rand.nextInt(groups.size());
	    			}while(newIndex == index);
	    			
	    			//System.out.println("before "+ groups.get(newIndex));
	    			updateGroup2Vehicle();
	    			while(!groups.get(newIndex).addTask(myTask)) {}
	    			//System.out.println("after ");

	    			
	    		}		
	    	}
	    	
	    	public void sequenceChangeTaskOrder() {
	    		int myIndex = 0;
	    		ArrayList<Integer> indexes = new ArrayList<Integer>();
	    		
	    		for (int i = 0 ;i<groups.size();i++) {
	    			if(groups.get(i).getGroupTasks().size()>=2) {
	    				indexes.add(i);
	    			}
	    		}
	    		if(indexes.size()!=0) {
	    			myIndex = indexes.get(rand.nextInt(indexes.size()));
	    			groups.get(myIndex).changeTaskOder();
	    		}
	    			//groups.add(groups.remove(rand.nextInt(groups.size())));
				
			}
	    	
	    	
//	    	private void updateSequenceEvent() {
//	    		for (int i = 0; i<groups.size();i++) {
//	    			groups.get(i).updateEvent();
//	    		}
//	    	}
	    	
		public void changeGroupOrder() {
    		if(groups.size()>0)
    			groups.add(groups.remove(rand.nextInt(groups.size())));
			
		}
		
		public void addGroup(Group newGroup) {
    			if(groups.size()>0)
    			{
    				groups.add(rand.nextInt(groups.size()),newGroup);
    			}else
    			{
    				groups.add(newGroup);
    			}
				
		}
	    	
		public Sequence(Sequence sequence) {
	    		this.seqVehicle = sequence.getSeqVehicle();
	    		for (int i = 0;i<sequence.groups.size();i++) {
	    			this.groups.add(new Group(sequence.groups.get(i)));
	    		};
	    		this.seqCost = sequence.getSeqCost();
		}
	    	
		public void printGroups() {
	    	//	System.out.println("the groups events are as follow");
    			for(int i=0;i<this.groups.size();i++) 
    			{
    				System.out.println("for the group "+i);
    				ArrayList<Event> tempEvents = groups.get(i).getGroupEvents();
    				for(int j=0; j<tempEvents.size();j++)
    				{
    					System.out.println("we "+tempEvents.get(j).getStatus()+" task "+tempEvents.get(j).getTask());
    				}
    			}
	    	}
		
	    	public void updateGroup2Vehicle() {
	    	
	    		for(int i=0;i<groups.size();i++) {
	    			group2Vehicle.put(groups.get(i), seqVehicle);
	    		}
	    	}
	    	
	    	public ArrayList<Group> getGroups() {
	    		return groups;
	    	}
	    	public Vehicle getSeqVehicle(){
	    		return seqVehicle;
	    	}
	    	public double getSeqCost() {
	    		return seqCost;
	    	}
	    	public void setVehicle(Vehicle V) {
	    		seqVehicle=V;
	    	}
	    	public void switch2Groups(int index1,int index2){
	    		Group temp = new Group(groups.get(index1));								//not sure if it is working but meh
	    		
	    		groups.add(index1, groups.get(index2));
	    		groups.add(index2,temp);
	    	}
	    private ArrayList<Event> getSeqEvents(){
	    		
	    		ArrayList<Event> seqEvents = new ArrayList<Event>();
	    		
	    		for(int i=0;i<groups.size();i++)
	    		{
	    			seqEvents.addAll(groups.get(i).getGroupEvents());
	    		}
	    		return seqEvents;
	    	}
	    public int getMaxWeight()
	    {    
	    		int maxWeight=0;
		    	
		    	for(int i=0;i<groups.size();i++)
		    	{
		    		if(maxWeight<groups.get(i).getMaxWeight())
		    		{
		    			maxWeight = groups.get(i).getMaxWeight();
		    		}
		    	}
		    	return maxWeight;
	    }
	    public double updateSeqCost() {
	    	
	    		
	    		
	    		City myCity=seqVehicle.getCurrentCity();
	    		Event myEvent=null;
	    		ArrayList<Event> seqEvents = new ArrayList<Event>(this.getSeqEvents()); //might not work
	    		seqCost = 0;
	    		for(int i=0;i<seqEvents.size();i++)
	    		{
	    			myEvent = seqEvents.get(i);
	    			if(myEvent.getStatus()==Status.PICKUP)
	    			{
	    				seqCost += myCity.distanceTo(myEvent.getTask().pickupCity);
	    				myCity = myEvent.getTask().pickupCity;
	    			}else 
	    			{
	    				seqCost += myCity.distanceTo(myEvent.getTask().deliveryCity);
	    				myCity = myEvent.getTask().deliveryCity;
	    			}
	    		}
	    		seqCost *= seqVehicle.costPerKm();
	    		return seqCost;
	    }
	    
	    	public Plan planGenerator() {
	    		City myCity=seqVehicle.getCurrentCity();

	    		Plan seqPlan = new Plan(myCity);
	    		
	    		Event myEvent=null;
	    		ArrayList<Event> seqEvents = new ArrayList<Event>(this.getSeqEvents()); //might not work
	    		
	    		
	    		for(int i=0;i<seqEvents.size();i++)
	    		{
	    			myEvent = seqEvents.get(i);
	    			if(myEvent.getStatus()==Status.PICKUP)
	    			{
					for (City path : myCity.pathTo(myEvent.getTask().pickupCity))
					{	
						
						seqPlan.appendMove(path);
					}

	    				seqPlan.appendPickup(myEvent.getTask());
	    				myCity = myEvent.getTask().pickupCity;
	    			}else 
	    			{
    					for (City path : myCity.pathTo(myEvent.getTask().deliveryCity))
    					{	
    						
    						seqPlan.appendMove(path);
    					}
	    				seqPlan.appendDelivery(myEvent.getTask());
	    				myCity = myEvent.getTask().deliveryCity;
	    			}
	    			
	    		}
	    		
	    		return seqPlan;
	    	}
    }
    private class Group{
    		
    		private ArrayList<Task> groupTasks = new ArrayList<Task>();
    		private ArrayList<Event> groupEvents = new ArrayList<Event>();
    		
    		
    		public Group (Group newGroup) {
    			this.groupTasks.clear();
    			this.groupTasks.addAll(newGroup.getGroupTasks());
    			this.groupEvents.clear();
    			for (int i = 0; i< newGroup.getGroupEvents().size();i++)
    			this.groupEvents.add(i,new Event(newGroup.getGroupEvents().get(i)));
    		}
    		
    		public Group(ArrayList<Task> myTasks, ArrayList<Event> myEvents) {
    			this.groupTasks.addAll(myTasks);
    			this.groupEvents.addAll(myEvents);
    		}

    		
    		public void changeTaskOder() {
    			ArrayList<Event> previousEvent = new ArrayList<Event> ();
    			
				for (int i = 0; i< groupEvents.size();i++)
	    			previousEvent.add(i,new Event(groupEvents.get(i)));
				
				while(previousEvent.equals(groupEvents)){
					Task myTask = removeTask(rand.nextInt(groupTasks.size()));
					addTask(myTask);
				}
    		}
    		
    		
    		
    		
    		
    		public void updateWeight(int eventIndex, int taskWeight)
    		{
    			for(int i=eventIndex+1;i<groupEvents.size();i++)
    			{
    				groupEvents.get(i).ChangeWeight(groupEvents.get(i).eventWeight+taskWeight);
    			}
    		}
    		public int getMaxWeight() {
    			
    			int maxWeight=0;
    			int currentWeight=0;
    			
    			for(int i=0;i<groupEvents.size();i++)
    			{
    				currentWeight = groupEvents.get(i).getWeight();
    				if(currentWeight>maxWeight)
    				{
    					maxWeight = currentWeight;
    				}
    			}
    			
    			return maxWeight;
    		}
    		
    		public ArrayList<Task> getGroupTasks(){
    			return groupTasks;
    		}
    		
    		public ArrayList<Event> getGroupEvents(){
    			return groupEvents;
    		}
    		
    		
    		
    		public Task removeTask(int taskIndex) {
    			
    			
    			//updateEvent();
    			
    		
    			//System.out.println("In Remove ");
    			Task myTask = groupTasks.remove(taskIndex);
    			
//    			for (int i = 0 ;i<groupEvents.size() ;i++) {
//    				System.out.println("before Remove : "+ groupEvents.get(i).getTask());
//    			}
    			
    			ArrayList<Integer> toRemove = new ArrayList<Integer>();
    			
    			for(int i=0;i<groupEvents.size();i++)
    			{
    				if(groupEvents.get(i).getTask()==myTask)
    				{
    					toRemove.add(i);
    					if (groupEvents.get(i).getStatus() == Status.DELIVER)
    					{
    						updateWeight(i, -myTask.weight);
    					}
    					else 
    					{
    						updateWeight(i, myTask.weight);
    					}
    				}
    			}
    			//System.out.println("After Remove : ");
    			if (toRemove.size()!=0) {
    				groupEvents.remove(toRemove.get(1).intValue());
    				groupEvents.remove(toRemove.get(0).intValue());
    				
//    				for (int i = 0 ;i<groupEvents.size() ;i++) {
//        				System.out.println("After Remove : "+ groupEvents.get(i).getTask());
//        			}
    			}
    			
    			
    			
    			
    			return myTask;
    			
    		}
    	
    		public boolean addTask(Task task) {
    			
    			//if the vehicle even empty cant contain the task
    		
    			
    			if(task.weight>group2Vehicle.get(this).capacity()) {
    				return false;
    			}
    			//if the vehicle is empty and can contain the task
    			if(groupTasks.size()==0) {
    				groupTasks.add(task);
    				groupEvents.add(new Event(task,Status.PICKUP,task.weight));
    				groupEvents.add(new Event(task, Status.DELIVER,0));
    				return true;
    			}
    			//if the vehicle has many tasks
    				
    			
    			ArrayList<Event> tempEvents = new ArrayList<Event>();
    			tempEvents.addAll(groupEvents);
    			
    			int pickupOrder = rand.nextInt((tempEvents.size()));
    			
    			while((tempEvents.get(pickupOrder).getWeight()+task.weight)>group2Vehicle.get(this).capacity() && pickupOrder!=tempEvents.size())
    			{
    				pickupOrder = rand.nextInt((tempEvents.size()));
    			}
    			
    			tempEvents.add(pickupOrder ,new Event(task,Status.PICKUP,tempEvents.get(pickupOrder).getWeight()+task.weight));
    				
    			int deliveryOrder = pickupOrder +1;
    			
    			this.updateWeight(pickupOrder+1,task.weight);
    			
    			int range = 0;
    			while(tempEvents.get(deliveryOrder+range).getWeight()<group2Vehicle.get(this).capacity() 
    					&& (deliveryOrder+range)<(tempEvents.size()-1))
    			{
    				range++;
    			}
    			
    			if(range!=0)
    			{
    				deliveryOrder = deliveryOrder+range;//rand.nextInt(range+1);							//danger
    				
    			}
    			
    			
    			
    			tempEvents.add(deliveryOrder,new Event(task,Status.DELIVER,tempEvents.get(deliveryOrder).getWeight()));
    			
    			this.updateWeight(deliveryOrder, -task.weight);
    			
    			if((pickupOrder==0 && deliveryOrder == 1) || (pickupOrder==tempEvents.size()-2))
    			{
    				return false;
    			}
    			groupEvents.clear();
    			groupEvents.addAll(tempEvents);
    			
    			return true;
    			
    		}
    		
//    		private void updateEvent(){
//    			if (groupTasks.size()==0)
//    				groupEvents.clear();
//    		}
    		
    }
    
    //Event class stores the action that should be taken next with the current carried weight
    
    private class Event{
    		private Task eventTask;
    		private Status eventStatus;
    		private int eventWeight;
    		
    		public Event(Task myTask, Status myStatus, int myWeight)
    		{
    			this.eventTask = myTask;
    			this.eventStatus = myStatus;
    			this.eventWeight = myWeight;
    		}
    		
    		public Event (Event newEvent) {
    			this.eventTask = newEvent.getTask();
    			this.eventStatus = newEvent.getStatus();
    			this.eventWeight = newEvent.getWeight();
    		}
    		
    		public void ChangeWeight(int newWeight)
    		{
    			this.eventWeight=newWeight;
    		}
    		public int getWeight() {
    			return eventWeight;
    		}
    		public Task getTask() {
    			return eventTask;
    		}
    		public Status getStatus() {
    			return eventStatus;
    		}
    }
    //**************************************************************
    
    

//    	public List<Plan> planListGenerator(){
//    		List<Plan> plans = new ArrayList<Plan>();
//    		
//    		
//    		for (Vehicle v : vehicles) {
//    			Plan planVehicle = new Plan(v.getCurrentCity());
//    			Task t1 = firstTask.get(v);
//    			while(t1!= null) {
//    				
//    				if (v.getCurrentCity() != t1.pickupCity ) {
//    					
//    		    		for (City path : v.getCurrentCity().pathTo(t1.pickupCity)){	
//    		    			planVehicle.appendMove(path);
//    					}
//    				}
//    				planVehicle.appendPickup(t1);
//    				
//    				for (City path : t1.pickupCity.pathTo(t1.deliveryCity)) {	
//		    			planVehicle.appendMove(path);
//					}
//    				planVehicle.appendDelivery(t1);
//    				
//    				t1 = nextTask.get(t1);	
//    			}
//    			plans.add(planVehicle);
//    		}
//    		return plans;
//    	}    	
    	
	
}
    
    
    
    
    

