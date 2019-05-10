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
    
    
    enum Algorithm { NAIVE, TYPE1, TYPE2 }
    
    Algorithm algorithm;
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
    	
    	
    	String algorithmName = agent.readProperty("algorithm", String.class, "TYPE1");
		
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
		
		System.out.println("Agent " );
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
        
		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		
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
        List<Plan> plan = null;
        
        Control bestOption = new Control (vehicles, tasks);
        Control myOption = new Control (vehicles, tasks);
        
        ArrayList<Control> listOfNeighbors = null;
       
        double cost = bestOption.costGenerator();
        
        int count = 0;
        
        System.out.print("	First list before the init ");
        Set set = bestOption.deliveryVehicle.entrySet();
	      Iterator iterator = set.iterator();
	      while(iterator.hasNext()) {
	         Map.Entry mentry = (Map.Entry)iterator.next();
	         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
	         System.out.println(mentry.getValue());
	      }
        
	      
        while(count<stopCondition) {
        	
        	System.out.print("	Best option is : ");
        	
            set = bestOption.deliveryVehicle.entrySet();
    	       iterator = set.iterator();
    	      while(iterator.hasNext()) {
    	         Map.Entry mentry = (Map.Entry)iterator.next();
    	         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
    	         System.out.println(mentry.getValue());
    	      }
        	 System.out.println("Stop");
        	 
        	 
        	listOfNeighbors.addAll(ChooseNeighbors(bestOption));							//dmder à JM
        	
        	
        	System.out.println("Stop");
        	myOption.updateValues(listOfNeighbors.remove(0));
        	
        	
        	while(myOption != null) {
        		System.out.println("debut while loop" + myOption.costGenerator() );
        		if(myOption.costGenerator()<cost) {
        			cost = myOption.cost;
        			bestOption.updateValues(myOption);												// probleme de memoire ici, on repasse par des états déjà fait -->perte de temps
        		}
        		System.out.println("before remove" );
        		if(listOfNeighbors.isEmpty())
        			break;
        		
        		myOption.updateValues(listOfNeighbors.remove(0));
        		System.out.println("end while loop" );
        	}
        	System.out.println("count" );
        	count++;
        }
        return bestOption.planListGenerator();
    }
    
    
    
    private ArrayList<Control> ChooseNeighbors(Control oldControl){
    	
    	System.out.print("In choose neighbors");
    	
    	ArrayList<Control> N = new ArrayList<Control>();
    	Control newControl = new Control(vehicles,tasks);
    	newControl.updateValues(oldControl);
    	Vehicle  myVehicle = null;
    	
//    	Set set = newControl.nextTask.entrySet();
//	      Iterator iterator = set.iterator();
//	      while(iterator.hasNext()) {
//	         Map.Entry mentry = (Map.Entry)iterator.next();
//	         System.out.print("key is: "+ mentry.getKey() + " & Value of new control is: ");
//	         System.out.println(mentry.getValue());
//	      }
//	      
//	      set = oldControl.nextTask.entrySet();
//	       iterator = set.iterator();
//	      while(iterator.hasNext()) {
//	         Map.Entry mentry = (Map.Entry)iterator.next();
//	         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
//	         System.out.println(mentry.getValue());
//	      }
//	      
    	
    	
    	
    	
    	
		do {
	    	Random rand = new Random();
	    	myVehicle = vehicles.get(rand.nextInt(vehicles.size()));					//ok
        }while(newControl.firstTask.get(myVehicle)==null);
    	System.out.print("myVehicle:" + myVehicle);
    	System.out.print("First task of myVehicle" + newControl.firstTask.get(myVehicle));
    	
    	
    	Task t = newControl.firstTask.get(myVehicle);
    	
//    	oldControl.nextTask.remove(t);
//    	oldControl.nextTask.put(t,t);
//    	
//    	  set = oldControl.nextTask.entrySet();
//	       iterator = set.iterator();
//	      while(iterator.hasNext()) {
//	         Map.Entry mentry = (Map.Entry)iterator.next();
//	         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
//	         System.out.println(mentry.getValue());
//	      }
//    	
//	      set = newControl.nextTask.entrySet();
//	       iterator = set.iterator();
//	      while(iterator.hasNext()) {
//	         Map.Entry mentry = (Map.Entry)iterator.next();
//	         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
//	         System.out.println(mentry.getValue());
//	      }
//   	
    	
    	System.out.print("Assigned vehcile" + newControl.deliveryVehicle.get(t));
    	
    	for (Vehicle v : vehicles ) {
    		    		
    		if(v!=myVehicle && t.weight<v.capacity()) {
    			newControl.changingVehicle(t,myVehicle,v);
    			N.add(newControl);
    			newControl.updateValues (oldControl);									//améliorer
    		}
    	}
    	
    	int length = 0;
	     
    	System.out.print("in function");
	  
    	
	   
    	while (newControl.nextTask.get(t)!=null) {
    		t = newControl.nextTask.get(t);
    		length++;
			//System.out.println(length);
			//System.out.println(t);
    	}
    	
    	if (length>=2) {
    		for (int ind1 = 1; ind1<=(length-1);ind1++) {
    			for (int ind2 = 1; ind2<=length&&ind2!=ind1;ind2++) {
    				System.out.println("before task order");
    				newControl.changingTaskOrder(myVehicle,ind1,ind2);
    				N.add(newControl);
    				newControl.updateValues(oldControl);
    			}
    		}
    	}
    	System.out.println("end neighbors");
    	return N;
    }
    
    
	private class Control  {
    	
		public HashMap<Task,Task> nextTask = new HashMap<Task,Task>();
        public HashMap<Vehicle,Task> firstTask = new HashMap<Vehicle,Task>();
        public HashMap<Task,Vehicle> deliveryVehicle = new HashMap<Task,Vehicle>();
        public TreeMap<Integer,Task> time = new TreeMap<Integer,Task>();
        
        public double cost = 0;
        
    	public Control (List<Vehicle> vehicles, TaskSet tasks){
    		
    		int max_capacity  = 0;
    		Vehicle bestVehicle = null;
    		
    		 System.out.println("Init control");
    		for (Vehicle v : vehicles) {
    			if (v.capacity()>max_capacity) {
    				max_capacity =v.capacity();
    				bestVehicle = v;
    			}
    			firstTask.put(v, null);
    		}
    		firstTask.remove(bestVehicle);
    		
    		
    		int i =1;
    		Task nextT= null;
    		for (Task t : tasks) {
              nextTask.put(t, nextT);
              nextT = t;
              deliveryVehicle.put(t,bestVehicle);
              }
    		
    		firstTask.put(bestVehicle,nextT);	
    		updateTime();
    		
//    		Set set = nextTask.entrySet();
//		      Iterator iterator = set.iterator();
//		      while(iterator.hasNext()) {
//		         Map.Entry mentry = (Map.Entry)iterator.next();
//		         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
//		         System.out.println(mentry.getValue());
//		      }
    		System.out.println("Init control end");
    		
    		
    	}
    	
    	public void updateValues(Control other) {
    		System.out.println("Update control begin");
    		System.out.print("In update values");
    		this.nextTask.putAll(other.nextTask);
    		this.firstTask.putAll(other.firstTask);
    		this.deliveryVehicle.putAll(other.deliveryVehicle);
    		this.time.putAll(other.time);
    		this.cost = other.cost;
    		System.out.println("Update control end");
    	}


    	
    	private void updateTime() {
    		
    		int compteur;
    		time.clear();
    		
    		for (Vehicle v : vehicles) {
    			compteur = 1;
    			Task t1 = firstTask.get(v);
    			if(t1 != null) {
    				System.out.println("In loop update");
    				time.put(compteur++,t1);
    				while (nextTask.get(t1)!=null) {
    					
    					t1 = nextTask.get(t1);
        				time.put(compteur++,t1);
    				}
    			}
    		}
    	}
    	
    	
    	private void changingVehicle(Task t,Vehicle v1, Vehicle v2) {
    		
    		
	         System.out.println("before update of changingVehicle");

    		Set set = deliveryVehicle.entrySet();
		      Iterator iterator = set.iterator();
		      while(iterator.hasNext()) {
		         Map.Entry mentry = (Map.Entry)iterator.next();
		         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
		         System.out.println(mentry.getValue());
		      }
		      System.out.println("our vehicle	"+ v1);
		      System.out.println("the vehicle	"+ v2);
		      System.out.println("task	"+ nextTask.get(t));
		      System.out.println("task	"+ nextTask.get(nextTask.get(t)));
		      

    		deliveryVehicle.remove (t);
    		deliveryVehicle.put(t,v2);
    		
			firstTask.remove(v1);
			firstTask.put(v1,nextTask.get(t));
			
			
			System.out.println("	"+ firstTask.get(v2));
			
			System.out.println("	"+ nextTask.get(t));
			
			nextTask.remove(t);
			nextTask.put(t,firstTask.get(v2));
			
			firstTask.remove(v2);
			firstTask.put(v2,t);
			
			System.out.println("	"+ t);
			System.out.println("	"+ nextTask.get(t));

	         System.out.println("After update of changingVehicle");

   		 set = nextTask.entrySet();
		       iterator = set.iterator();
		      while(iterator.hasNext()) {
		         Map.Entry mentry = (Map.Entry)iterator.next();
		         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
		         System.out.println(mentry.getValue());
		      }
		      set = deliveryVehicle.entrySet();
		       iterator = set.iterator();
		      while(iterator.hasNext()) {
		         Map.Entry mentry = (Map.Entry)iterator.next();
		         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
		         System.out.println(mentry.getValue());
		      }
		
			updateTime();	
			
    	}
    	
    	
    	public List<Plan> planListGenerator(){
    		List<Plan> plans = new ArrayList<Plan>();
    		
    		
    		for (Vehicle v : vehicles) {
    			Plan planVehicle = new Plan(v.getCurrentCity());
    			Task t1 = firstTask.get(v);
    			while(t1!= null) {
    				
    				if (v.getCurrentCity() != t1.pickupCity ) {
    					
    		    		for (City path : v.getCurrentCity().pathTo(t1.pickupCity)){	
    		    			planVehicle.appendMove(path);
    					}
    				}
    				planVehicle.appendPickup(t1);
    				
    				for (City path : t1.pickupCity.pathTo(t1.deliveryCity)) {	
		    			planVehicle.appendMove(path);
					}
    				planVehicle.appendDelivery(t1);
    				
    				t1 = nextTask.get(t1);	
    			}
    			plans.add(planVehicle);
    		}
    		return plans;
    	}    	
    	
    	
    	private void changingTaskOrder(Vehicle v1, int index1,int index2) {
    		
    		 System.out.println("CHangingTaskOrder Begin "+ index1 +" "+index2);
    		
    		if (index1 !=index2) {
    			
    		
    		Control NewControl = this;
    		Task tPre1 =  firstTask.get(v1);
    		Task t1 = nextTask.get(tPre1);
    		int count = 0; 	
    		
    		
    		// à modifier en fonction de l'index choisi								peut aussi le faire avec timer!!!!!
    		while(count < index1) {
				tPre1 = t1;
				t1 = nextTask.get(t1);
				count++;
    		}
    		
    		Task tPre2 = firstTask.get(v1);
    		Task t2 = nextTask.get(tPre2);
    		count = 0;
    		while(count < index2) {
    			if(t1!=null||tPre2 !=null) {
    				tPre2 = t2;
    				t2 = nextTask.get(t2);
    				count++;
    			}else 
    				System.out.println("Problem with the null condition in changingTaskOrder");	
    		}
    		Task tPost1 = nextTask.get(t1);
    		Task tPost2 = nextTask.get(t2);
    		// now we will invert the two of them
    		
    		if(index1==1) {
    			firstTask.remove(v1);
    			firstTask.put(v1,t2);
//    			nextTask.remove(t2);
//    			nextTask.put(t1,tPost2);
//    			nextTask.remove(tPre2);
//    			nextTask.put(tPre2,t1);
    		}
    		if (index2 == 1) {
    			firstTask.remove(v1);
    			firstTask.put(v1,t1);
//    			nextTask.remove(t1);
//    			nextTask.put(t2,tPost1);
//    			nextTask.remove(tPre1);
//    			nextTask.put(tPre1,t2);
    		}
    		
			nextTask.remove(t1);
			
			nextTask.remove(tPre1);
			nextTask.put(tPre1,t2);
			
			nextTask.remove(t2);
				
			nextTask.remove(tPre2);
			nextTask.put(tPre2,t1);
			nextTask.put(t1,tPost2);
			nextTask.put(t2,tPost1);
			
			
			
//			Set set = nextTask.entrySet();
//		      Iterator iterator = set.iterator();
//		      while(iterator.hasNext()) {
//		         Map.Entry mentry = (Map.Entry)iterator.next();
//		         System.out.print("key is: "+ mentry.getKey() + " & Value is: ");
//		         System.out.println(mentry.getValue());
//		      }
//		      
//		      System.out.println("Map key and values before processing :");
//		      Set set2 = NewControl.nextTask.entrySet();
//		      Iterator iterator2 = set2.iterator();
//		      while(iterator2.hasNext()) {
//		          Map.Entry mentry2 = (Map.Entry)iterator2.next();
//		          System.out.print("Key is: "+mentry2.getKey() + " & Value is: ");
//		          System.out.println(mentry2.getValue());
//		       }
		      
		      updateTime();
		      }
   		 System.out.println("CHangingTaskOrder Begin");

    	}
    	
    	public double costGenerator() {
    		
        	Task t1 = null;
 
        	for (Task t : tasks) {
        		t1 = nextTask.get(t);
        		if (t1!=null) {
        			cost += (t.deliveryCity.distanceTo(t1.pickupCity) + t1.pickupCity.distanceTo(t1.deliveryCity))*deliveryVehicle.get(t).costPerKm();
        		}
        	}

        	for (Vehicle v : vehicles ) {
        		t1 = firstTask.get(v);
        		if(t1 != null) {
        			cost += (v.getCurrentCity().distanceTo(t1.pickupCity) + t1.pickupCity.distanceTo(t1.deliveryCity))*v.costPerKm();
        		}
        	}
        	
        	return cost;
        }
	}
	
	
}
    
    
    
    
    

