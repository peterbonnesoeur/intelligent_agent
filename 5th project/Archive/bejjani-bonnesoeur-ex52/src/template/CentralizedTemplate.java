package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.Action;

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
    
    private int nbTasks;
    private double probability;
    private int init;
    private int nbVehicles;
	private int[] taskWeightTable;
	private int[] nextActionTable;
	private ArrayList<Task> myTasks;
	private ArrayList<Vehicle> myVehicles;
	private ArrayList<City> myPickupCities;
	private ArrayList<City> myDeliveryCities;


	
	
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
    	
   
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
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
        long time_start = System.currentTimeMillis();
        int i,j;
        int iter = 0;
        
        ///////////////// TO CHANGE FOR DIFFERENT PERFORMANCES ////////////////
        probability = 0.2;
        init = 0; // ---> even for not random, odd for random
        ///////////////////////////////////////////////////////////////////////
        
        
        int maxIter = 1000;
        nbTasks = tasks.size();
		nbVehicles = vehicles.size();
		nextActionTable = new int[2*nbTasks+nbVehicles];
		ArrayList<Task> myTasks = new ArrayList<Task>();
        myTasks.addAll(tasks);
        
        ArrayList<int[]> N = new ArrayList<int[]>();
        ArrayList<City> myPickupCities = new ArrayList<City>();
        ArrayList<City> myDeliveryCities = new ArrayList<City>();
        taskWeightTable = new int[nbTasks];
        

        tablesInit(vehicles, nbTasks, nbVehicles, myTasks, myPickupCities, myDeliveryCities, taskWeightTable);
		List<Plan> plans = new ArrayList<Plan>();

		for(i = 0; i<nbVehicles;i++) 
			plans.add(makePlan(vehicles.get(i), i,myTasks,myPickupCities, myDeliveryCities,nextActionTable));
		
		while(iter < maxIter) {
			
			N = chooseNeighbors(plans,vehicles, nextActionTable);			
			nextActionTable = localChoice(N, vehicles,myTasks,myPickupCities,myDeliveryCities);
	
			List<Plan> newPlans = new ArrayList<Plan>();
			
			for(i = 0;i < nbVehicles;i++)
				newPlans.add(makePlan(vehicles.get(i), i, myTasks, myPickupCities,myDeliveryCities,nextActionTable));
			
			iter++;
			if(calculateCost(plans,vehicles) == calculateCost(newPlans,vehicles)) {
				break;
			}
			else 
				plans = newPlans;
			
		}

        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        
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
    
    private Plan makePlan(Vehicle vehicle, int vehicleIndex, ArrayList<Task> myTasks,
    		ArrayList<City> myPickupCities,ArrayList<City> myDeliveryCities,int[] actionTable) {
    	
    	City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);
        
    	boolean exit = false;
    	int tmp;
    	tmp = actionTable[2*nbTasks+vehicleIndex];
    	while(!exit) {
    		
    		if(tmp != 2*nbTasks) {
    			if(tmp%2 == 0) {//case pickup 				
    				for (City city : current.pathTo(myPickupCities.get(tmp/2))) {
    	                plan.appendMove(city);
    	            }
    				plan.appendPickup(myTasks.get(tmp/2));
    				current = myPickupCities.get(tmp/2);
    				tmp = actionTable[tmp];
    				
    			}
    			else if(tmp%2 == 1) {//case delivery
    				for (City city : current.pathTo(myDeliveryCities.get(tmp/2))) {
    					plan.appendMove(city);
    				}
    				plan.appendDelivery(myTasks.get(tmp/2));
    				current = myDeliveryCities.get(tmp/2);
    				tmp = actionTable[tmp];
    				
    			}
    			
    		}
    		else {
    			exit = true;
    		}
    		
    	}
    	return plan;
    	
    }
    
    private void tablesInit(List<Vehicle> vehicles, int nbTasks, int nbVehicles, 
    		ArrayList<Task> myTasks,ArrayList<City> myPickupCities,ArrayList<City> myDeliveryCities, int[] taskWeightTable) {
    	
    	int i,idx;
    	int maxCapacityVehicle = 0;
    	int capacity = vehicles.get(0).capacity();
    	
    	for(i = 1; i < nbVehicles;i++) {
    		if(vehicles.get(i).capacity()>capacity) {
    			capacity = vehicles.get(i).capacity();
    			maxCapacityVehicle = i;
    		}
    	}
    	/*for(i = 1; i < nbTasks;i++) {
    		if(myTasks.get(i).weight > biggestTask) {
    			biggestTask = myTasks.get(i).weight;
    			maxTaskWeight = i;
    		}
    	}*/
         
        for(i = 0;i<nbTasks; i++) {
        	myPickupCities.add(myTasks.get(i).pickupCity);
        	myDeliveryCities.add(myTasks.get(i).deliveryCity);
        	taskWeightTable[i] = myTasks.get(i).weight;
        }
        
        if(init%2 == 1) {
        	for(i = 0;i<2*nbTasks+nbVehicles;i++) {
        		nextActionTable[i] = 2*nbTasks;
        	}
        
        	for(i = 0;i < nbTasks;i++) {
        		int numV = (int)(Math.random()*nbVehicles-0.01); 
        		idx = 2*nbTasks + numV; 
        		while(nextActionTable[idx] != 2*nbTasks) {
        			idx = nextActionTable[idx];
        		}
        		if(myTasks.get(i).weight < vehicles.get(numV).capacity()) {
        			nextActionTable[idx] = 2*i;
        			idx = nextActionTable[idx];
        			nextActionTable[idx] = 2*i + 1;
        		}
        	else
        		i--;
        	}
        }
        else {
        	for(i = 0;i<2*nbTasks; i++) {
        		nextActionTable[i] = i+1;
        	}
        	for(i = 2*nbTasks;i<2*nbTasks+nbVehicles; i++) {
        		nextActionTable[i] = 2*nbTasks;
        	}
        	nextActionTable[2*nbTasks+maxCapacityVehicle] = 0;
        }
        
    }
    
    private int[] localChoice(ArrayList<int[]> N, List<Vehicle> vehicles,ArrayList<Task> myTasks,
    		ArrayList<City> myPickupCities,ArrayList<City> myDeliveryCities){
    	
    	int[] bestChoice = nextActionTable;
    	ArrayList<Plan> firstPlans = new ArrayList<Plan>();
    	double cost = 0;
    	double newCost=0;
       	int i,j,k;
    	
    	for(i = 0; i<vehicles.size(); i++) {
			firstPlans.add(makePlan(vehicles.get(i), i, myTasks,myPickupCities,myDeliveryCities,nextActionTable));
		}
    	cost = calculateCost(firstPlans,vehicles);
    	
    	for(j = 0; j< N.size();j++) {
    		
    		ArrayList<Plan> plans = new ArrayList<Plan>();
    		for(i = 0; i<vehicles.size(); i++) {   			
    			plans.add(makePlan(vehicles.get(i), i, myTasks,myPickupCities,myDeliveryCities,N.get(j)));
    		}
    		newCost = calculateCost(plans,vehicles);
    		if(newCost <  cost ) {
    			if(Math.random() < probability) {
    				cost = newCost;
    				bestChoice = N.get(j);
    			}
    		}

    	}
    	return bestChoice;
    }

    
    private ArrayList<int[]> chooseNeighbors(List<Plan> plans,List<Vehicle> vehicles, int[] actionTable) {
    	
    	int i,j,k,l,tmp,tmp2;
    	
    	ArrayList<int[]> N = new ArrayList<int[]>();
    	
    	//Case swap <-----------
    	for(i = 0; i<2*nbTasks+nbVehicles-2;i++) {
    		for(j = i+1; j<2*nbTasks+nbVehicles-1;j++ ) {
    			  			
    			boolean validOrder = true;
    	    	boolean validWeight = true;
    	    	int newActionTable[] = new int[2*nbTasks+nbVehicles];

    	    	for(l = 0;l<2*nbTasks+nbVehicles;l++)
    	    		newActionTable[l] = actionTable[l];
    	    	
    			tmp = newActionTable[i];
    			newActionTable[i] = newActionTable[j];
    			tmp2 = newActionTable[j];
    			newActionTable[j] = newActionTable[tmp2];
    			newActionTable[tmp2] = tmp;
    			    			
    			
    			validOrder = controlOrder(newActionTable);
    			for(k = 0;k < nbVehicles;k++) {
    	    		if(validOrder && validWeight) {   	    			
    	    			validWeight = controlWeight(k, vehicles.get(k).capacity(), newActionTable);
    	    		}
    	    		else {
    	    			break; 	
    	    		}
    	    	}
    			if(validOrder && validWeight)  {
    				N.add(newActionTable);
    			}
    		}    		
    	}
    	
    	//case give
    	for(i = 0; i<2*nbTasks+nbVehicles-1;i++) {
    		for(j = i+1; j<2*nbTasks+nbVehicles;j++ ) {
    			  			
    			boolean validOrder = true;
    	    	boolean validWeight = true;
    	    	int newActionTable[] = new int[2*nbTasks+nbVehicles];

    	    	for(l = 0;l<2*nbTasks+nbVehicles;l++)
    	    		newActionTable[l] = actionTable[l];
    	    	
    	    	if(newActionTable[i] != 2*nbTasks && newActionTable[j] != 2*nbTasks) {
    	    		 
    	    		validOrder = controlOrder(newActionTable);
    	    		for(k = 0;k < nbVehicles;k++) {
    	    			if(validOrder && validWeight) {   	    			
    	    				validWeight = controlWeight(k, vehicles.get(k).capacity(), newActionTable);
    	    			}
    	    			else {
    	    				break; 	
    	    			}
    	    		}
    	    		if(validOrder && validWeight){	
    	    			N.add(newActionTable);   	
    	    		}
    	    	}
    		}
    	}	
    	return N;   	
    }
    
    private boolean controlOrder(int[] actionTable) {
    	boolean valid = true;
    	
    	int controlTasks[] = new int[nbTasks];
    	int i,k;

    	
    	for(i = 0;i<2*nbTasks;i++) {
			if(actionTable[i] == i)
				valid = false;
		}
    	for(i = 0; i<nbTasks;i++) {
    		controlTasks[i] = -1;
    	}
    	
    	for(k = 0;k < nbVehicles;k++) {
    		
    		int idx = 2*nbTasks+k;
    		int iter = 0;
    		
    		while(actionTable[idx] != 2*nbTasks && valid) {
    			if(isPickup(actionTable[idx])) {
    				if(controlTasks[actionTable[idx]/2] != -1)
    					valid = false;
    				else
    					controlTasks[actionTable[idx]/2] = nbTasks*(k+1);					
    			}
    			if(!isPickup(actionTable[idx])) {
    				if(controlTasks[actionTable[idx]/2] == 0 || iter == 0)
    					valid = false;
    				else
    					controlTasks[actionTable[idx]/2] -= nbTasks*(k+1);
    			}   	
    			idx = actionTable[idx];
    			iter ++;
    		}
		}
		
		for(i = 0;i < nbTasks;i++){
			if(controlTasks[i] != 0){
				valid = false;
			}
		} 
		return valid;
    }
    
    private boolean controlWeight(int vehicleNumber, int freeWeight, int[] actionTable) {
    	boolean valid = true;
    	int idx = 2*nbTasks+vehicleNumber;
    	
		while(actionTable[idx] != 2*nbTasks && valid) {
			
			if(isPickup(actionTable[idx])) {
				freeWeight -= taskWeightTable[actionTable[idx]/2];
				if(freeWeight < 0)
					valid = false;
			}
			else {
				freeWeight += taskWeightTable[actionTable[idx]/2];
			}	
			
			idx = actionTable[idx];
		}
		return valid;
    }
    
    
    
    
    
    private boolean isPickup(int actionIndex) {
    	if(actionIndex%2== 1)
    		return false;
    	else
    		return true; 			
    }
    
    
    
    public double calculateCost(List<Plan> plans,List<Vehicle> vehicles) {
    	double myCost = 0;
    	int i;
    	for(i = 0;i < nbVehicles;i++) {
    		myCost += plans.get(i).totalDistance()*vehicles.get(i).costPerKm();
    	}
    	return myCost;
    	
    }
    
}
