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
				plans = optimizedPlanV1(vehicles,tasks);
				break;
			case TYPE2:
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
    
    private List<Plan> optimizedPlanV1(List<Vehicle> vehicles, TaskSet tasks) {
    	
    		
        Coordination coord = new Coordination(vehicles,tasks);
        System.out.println("coordination is created" );
    		List<Plan> plan = coord.getPlans();
        
        
        return plan;
    }
    
    private class Coordination{
    		
    		private HashMap<Vehicle,Sequence> sequences = new HashMap<Vehicle,Sequence>();
    		
    		
    		public Coordination(List<Vehicle> myVehicles, TaskSet myTasks) {
    			
    			int nbTasks = 0;
    			TaskSet TaskLeft = null;
    			TaskLeft = TaskSet.copyOf(myTasks);
    			System.out.println("we are creating coordination for "+myVehicles.size()+" vehicles with "
    			+ TaskLeft.size()+" tasks");
    			int iterV=0;
    			for(Vehicle V:myVehicles)
    			{
    				System.out.println("for the vehicle "+V.id() );
    				if(iterV!=myVehicles.size()-1)
    				{
    					nbTasks = new Random().nextInt((TaskLeft.size()));
    				}
    				else 
    				{
    					nbTasks = TaskLeft.size();
    				}
    				System.out.println("we are adding "+nbTasks+" Tasks" );
    				ArrayList<Task> seqTasks = new ArrayList<Task>();
    				
    				
    				for(int i=0; i<nbTasks;i++)
    				{
        				int indexTask = new Random().nextInt((TaskLeft.size()));
        				int j=0;
        				Task selectedTask=null;
    					
        				for(Task iterTask:TaskLeft) {
        					
    						if(j==indexTask)
    						{
    							selectedTask = iterTask;
    							break;
    						}
    						j++;
    					}
        				TaskLeft.remove(selectedTask);
    						
    					seqTasks.add(selectedTask);
    				}
    				System.out.println("the seqTasks is "+seqTasks );
    				System.out.println("the task left are "+TaskLeft );
    				sequences.put(V, new Sequence(seqTasks,V));
    				
    				iterV++;
    			}
    			
    		}
    		public List<Plan> getPlans(){
    			List<Plan> plans = new ArrayList<Plan>();
    			for(Vehicle V : vehicles) {
    				plans.add(sequences.get(V).planGenerator());
    			}
    			return plans;
    		}
    	
    	
    }
    private class Sequence{
    		
	    	private ArrayList<Group> groups = new ArrayList<Group>();
	    	private Vehicle seqVehicle=null;
	    	private double seqCost = 0;
	    	
	    	public Sequence(ArrayList<Task> seqTasks,Vehicle currentVehicle) {
	    		
	    		System.out.println("we are creating a sequence of "+seqTasks.size()+" groups" );
	    		
	    		this.seqVehicle = currentVehicle;
	    		
	    		if(seqTasks!=null)
	    		{
	    			
	    			for(int i=0;i<seqTasks.size();i++)
	    			{
	    				//might have to check the equalities in the objects instantiation.
	    				Group currentGroup = new Group();
	    				this.groups.add(currentGroup);
	    				
	    				//not sure the hash key will update groups(probably yes)
	    				
	    				group2Vehicle.put(currentGroup, currentVehicle);
	    				
	    			}
	    			for(Task task:seqTasks) 
	    			{
	    				System.out.println("we are adding task "+task );
	    				int i=0;
	    				do {
	    				i = new Random().nextInt((this.groups.size()));
	    				System.out.println("to group "+i);
	    				}while(!this.groups.get(i).addTask(task));
	    				
	    			}
	    			
	    			System.out.println("the groups events are as follow");
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
	    	}
	    	
	    	public void updateGroup2Vehicle() {
	    		for(int i=0;i<groups.size();i++) {
	    			group2Vehicle.put(groups.get(i), seqVehicle);
	    		}
	    	}
	    	
	    	public ArrayList<Group> getGroups() {
	    		return groups;
	    	}
	    	public double getSeqCost() {
	    		return seqCost;
	    	}
	    	public void switch2Groups(int index1,int index2){
	    		Group temp = new Group();								//not sure if it is working but meh
	    		temp.copy(groups.get(index1));
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
	    double findSeqCost() {
	    		
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
	    		
	    		System.out.println("New Plan ");
	    		for(int i=0;i<seqEvents.size();i++)
	    		{
	    			myEvent = seqEvents.get(i);
	    			if(myEvent.getStatus()==Status.PICKUP)
	    			{
	    				System.out.println("Current City: "+ myCity);
	    				System.out.println("pickUp City: "+ myEvent.getTask().pickupCity);
//	    				if (myCity != myEvent.getTask().pickupCity ) 
//	    				{
	    					for (City path : myCity.pathTo(myEvent.getTask().pickupCity))
	    					{	
	    						System.out.println(" City Path: "+ path);
	    						seqPlan.appendMove(path);
	    					}
//	    				}
	    				seqPlan.appendPickup(myEvent.getTask());
	    				myCity = myEvent.getTask().pickupCity;
	    			}else 
	    			{
//	    				if (myCity != myEvent.getTask().deliveryCity ) 
//	    				{
	    					System.out.println("Current City: "+ myCity);
		    				System.out.println("Delivery City: "+ myEvent.getTask().deliveryCity);
	    					for (City path : myCity.pathTo(myEvent.getTask().deliveryCity))
	    					{	
	    						System.out.println(" City Path: "+ path);
	    						seqPlan.appendMove(path);
	    					}
//	    				}
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
    		
    		public void copy(Group newGroup) {
    			this.groupTasks.clear();
    			this.groupTasks.addAll(newGroup.getGroupTasks());
    			this.groupEvents.clear();
    			this.groupEvents.addAll(newGroup.getGroupEvents());
    		}
    		
    		public Task removeTask(int taskIndex) {
				
    			Task myTask = groupTasks.remove(taskIndex);
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
    			groupEvents.remove(toRemove.get(0).intValue());
    			groupEvents.remove(toRemove.get(1).intValue());
    			
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
    				System.out.println("we are adding "+task+"to "+groupTasks);
    			
    			ArrayList<Event> tempEvents = new ArrayList<Event>();
    			tempEvents.addAll(groupEvents);
    			
    			int pickupOrder = new Random().nextInt((tempEvents.size()));
    			
    			while((tempEvents.get(pickupOrder).getWeight()+task.weight)>group2Vehicle.get(this).capacity() && pickupOrder!=tempEvents.size())
    			{
    				pickupOrder = new Random().nextInt((tempEvents.size()));
    			}
    			
    			tempEvents.add(pickupOrder ,new Event(task,Status.PICKUP,tempEvents.get(pickupOrder).getWeight()+task.weight));
    				System.out.println("we are adding the pickup at index "+pickupOrder);
    			int deliveryOrder = pickupOrder +1;
    			
    			this.updateWeight(pickupOrder+1,task.weight);
    			
    			int range = 0;
    				System.out.println("temp Event size is: "+tempEvents.size());
    				System.out.println("the current weight after pickup is "+tempEvents.get(deliveryOrder+range).getWeight());
    			while(tempEvents.get(deliveryOrder+range).getWeight()<group2Vehicle.get(this).capacity() 
    					&& (deliveryOrder+range)<(tempEvents.size()-1))
    			{
    				range++;
    				System.out.println("index: "+(deliveryOrder+range));
    			}
    			
    			if(range!=0)
    			{
    				deliveryOrder = deliveryOrder+range;//new Random().nextInt(range+1);							//danger
    				System.out.println("randomRange : "+new Random().nextInt(range));
    			}
    			
    			
    			
    			tempEvents.add(deliveryOrder,new Event(task,Status.DELIVER,tempEvents.get(deliveryOrder).getWeight()));
    			System.out.println("we are adding the deliver at index "+deliveryOrder);
    			this.updateWeight(deliveryOrder, -task.weight);
    			
    			if((pickupOrder==0 && deliveryOrder == 1) || (pickupOrder==tempEvents.size()-2))
    			{
    				return false;
    			}
    			groupEvents.clear();
    			groupEvents.addAll(tempEvents);
    			
    			return true;
    			
    		}
    		
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
    
    
    
    
    

