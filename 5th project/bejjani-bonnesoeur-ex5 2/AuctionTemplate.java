package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
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
 *
 */

// PROBLEME LORSQUE LA SEULE TASK EST PAS LA TASK 0 --> pas mettre 2x nbTasks
@SuppressWarnings("unused")
public class AuctionTemplate implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;
	
	private ArrayList<Task> myTasks;
	private ArrayList<Task> opponentTasks;
	private ArrayList<Vehicle> myVehicles;
	private ArrayList<Vehicle> opponentVehicles;
	private ArrayList<City> myPickupCities;
	private ArrayList<City> opponentPickupCities;
	private ArrayList<City> myDeliveryCities;
	private ArrayList<City> opponentDeliveryCities;
	private ArrayList<Long[]> bidList;
	private ArrayList<Long> predictedBids;
	private List<City> myCities;
	private int[] nextActionTable;
	private int[] opponentNextActionTable;
	private int[] potentialNextActionTable;
	private int[] opponentPotentialNextActionTable;
	private int[] bestCities;
	
	private double[] cityWeights;
	private double meanCityWeight;
	
	private int nbBid;
	private int nbCities;


	private int nbVehicles;
	private int nbTasks;
	private int opponentNbTasks;
	private int iter1, iter2;

//	ArrayList<Task> myTasks = new ArrayList<Task>();

    
	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();
			
		this.myCities = topology.cities();
		this.myVehicles = new ArrayList<Vehicle>();
		this.opponentVehicles = new ArrayList<Vehicle>();
		this.myTasks= new ArrayList<Task>();
		this.myPickupCities = new ArrayList<City>();
		this.myDeliveryCities = new ArrayList<City>();
		this.bidList = new ArrayList<Long[]>();
		this.predictedBids = new ArrayList<Long>();
		

		
		this.opponentTasks= new ArrayList<Task>();
		this.opponentPickupCities = new ArrayList<City>();
		this.opponentDeliveryCities = new ArrayList<City>();		
		
		nbBid = 0;
        nbVehicles = agent.vehicles().size();
        
        nbTasks = 0;
        opponentNbTasks = 0;
        
        nbCities = myCities.size();
        cityWeights = new double[nbCities];
        meanCityWeight = 0;
        bestCities = new int[nbCities];
        
        nextActionTable = new int[nbVehicles];
        opponentNextActionTable = new int[nbVehicles];
        
        potentialNextActionTable = new int[nbVehicles+2];
        opponentPotentialNextActionTable = new int[nbVehicles+2];
        
        tablesInit();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		int i;
		
		bidList.add(bids);
		
		updateTables(previous, winner);
//		System.out.print("winner: "+ winner+ "\n");

	}
	
	@Override
	public Long askPrice(Task task) {
				
		updatePotentialTables(task);
		
		double marginalCost = computeMarginalCost(nextActionTable,potentialNextActionTable, myPickupCities, myDeliveryCities, myTasks, false);
		double opponentMarginalCost = computeMarginalCost(opponentNextActionTable,opponentPotentialNextActionTable, opponentPickupCities, opponentDeliveryCities, opponentTasks, true);

		double bid;
		double bid2 = marginalCost;
		//double bid = bid(task,marginalCost);
		double opponentBid = opponentMarginalCost;
		
		if(agent.id() == 0) {
			bid = bid(task,marginalCost, opponentMarginalCost);
//			System.out.print("My bid "+nbBid+" :"+ bid+"\n");
			
		}
		else {
			bid = bid(task,marginalCost, opponentMarginalCost);
//			System.out.print("Opponent bid "+nbBid+" :"+ bid+"\n");		
		}
//		System.out.print("opponents bid: "+ opponentBid+"\n");
		nbBid++;
		System.out.print("Biding for task : " + task.id + "\n");
		System.out.print("Sayids Bid : " + bid + "\n");
		return (long) bid;

	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
		System.out.println("Agent " + agent.id() + " has tasks " + tasks+"\n");
		
//		ArrayList<Task> taskSet = new ArrayList<Task>();
//		taskSet.addAll(tasks);
		int i;
//		for(i = 0;i < nextActionTable.length;i++)
//			System.out.print(nextActionTable[i]+ " ");
		List<Plan> plans = new ArrayList<Plan>();
		List<Plan> bestPlans = new ArrayList<Plan>();
		
//		for(i = 0;i<nbVehicles;i++) {
//			plans.add(makePlan(myVehicles.get(i), i, nextActionTable, myPickupCities, myDeliveryCities, taskSet));
//		}
//		
		CentralizedTemplate test = new CentralizedTemplate(); 
		
		bestPlans = test.plan(myVehicles, tasks);
		double bestCost = test.calculateCost(bestPlans, myVehicles) ;
		double cost;
		for(i = 0;i < 100;i++) {
			plans = test.plan(myVehicles, tasks);
			cost = test.calculateCost(plans, myVehicles);
			if(cost < bestCost) {
				bestCost = cost;
				bestPlans.clear();
				bestPlans.addAll(plans);
			}
				
		}
		long reward = 0;
		for(Task task : tasks)
			reward += task.reward;
		
		System.out.print("My Profit : " + (reward-bestCost) + "\n ");

		return bestPlans;
	}
	
	private double bid(Task task, double marginalCost, double opponentMarginalCost) {
		double bid = marginalCost;
		double opponentBid = opponentMarginalCost;
		int i;
		
		double errorRatio = 0.9;
		double weightRatio = 1;
		
		if(cityWeights[task.pickupCity.id] < meanCityWeight) {
			bid += weightRatio*cityWeights[task.pickupCity.id];
			opponentBid += weightRatio*cityWeights[task.pickupCity.id];
		}
		else {
			bid -= weightRatio*cityWeights[task.pickupCity.id];
			opponentBid -= weightRatio*cityWeights[task.pickupCity.id];
		}
		if(cityWeights[task.deliveryCity.id] < meanCityWeight) {
			bid += weightRatio*cityWeights[task.deliveryCity.id];
			opponentBid += weightRatio*cityWeights[task.deliveryCity.id];
		}
		else {
			bid -= weightRatio*cityWeights[task.deliveryCity.id];
			opponentBid -= weightRatio*cityWeights[task.deliveryCity.id];
		}
			
		double predictError = 0;
		double bidRatio = 0;
		
		for(i = 0; i < bidList.size();i++) {
			predictError += (bidList.get(i)[0] - predictedBids.get(i))/bidList.size();
		}
		
		predictedBids.add((long) opponentBid);
		
		for(i = 0;i < bidList.size(); i++) {
			double myBid = bidList.get(i)[agent.id()];
			double theirBid;
			if(agent.id() == 0)
				theirBid = bidList.get(i)[1];
			else
				theirBid = bidList.get(i)[0];
			double size = bidList.size();
			bidRatio += (theirBid-myBid)/(myBid*size);
		}
	
		bid += 0.5*bidRatio*bid;
		
		if((opponentBid+errorRatio*predictError) > 3000 && bid < 1000) {
			bid = 1.5*bid;
		}

		
		if(bid<500 && ((opponentBid+errorRatio*predictError)-bid)> 0) {
			bid = 0.8*(opponentBid+errorRatio*predictError);
		}
		
		if(task.id < 5 && nextActionTable.length <= nbVehicles) {
			bid = bid*((5.0 - (double) task.id)/5.0);
		}
		
		if(bid < 300)
			bid = 500;
		
		return bid;
	}
	
	private double computeMarginalCost(int[] actionTable,int[] potActionTable, ArrayList<City> pCities, ArrayList<City> dCities, ArrayList<Task> tasks, boolean opponent) {
		int i;
		double cost, newCost, marginalCost;
		ArrayList<Plan> plans = new ArrayList<Plan>();
		ArrayList<Plan> newPlans = new ArrayList<Plan>();

		
		for(i = 0;i<nbVehicles;i++) {
			if(!opponent) {
				plans.add(makePlan(myVehicles.get(i).getCurrentCity(), i, actionTable, pCities, dCities, tasks));
				newPlans.add(makePlan(myVehicles.get(i).getCurrentCity(), i, potActionTable, pCities, dCities, tasks));
			}
			else {
				plans.add(makePlan(myCities.get(bestCities[0]), i, actionTable, pCities, dCities, tasks));
				newPlans.add(makePlan(myCities.get(bestCities[0]), i, potActionTable, pCities, dCities, tasks));
			}
				
		}

		cost = calculateCost(plans);
		newCost = calculateCost(newPlans);
		marginalCost = newCost-cost;
		return marginalCost;
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	private void  tablesInit() {
		int i,j;
		for(i = 0;i<nbVehicles;i++) {
			nextActionTable[i] = 0;	
			opponentNextActionTable[i] = 0;	
		}
		myVehicles.addAll(agent.vehicles());

		double totalDistance = 0;
		
		for(i = 0;i<nbCities;i++) {
			for(j = 0;j < nbCities;j++) {
				if(myCities.get(i).hasNeighbor(myCities.get(j))) {
					totalDistance += myCities.get(i).distanceTo(myCities.get(j))/2;
				}
			}
		}
		
		for(i = 0;i<nbCities;i++) {
			cityWeights[i] = 0;
			for(j = 0;j < nbCities;j++) {
				if(i!=j) {
					cityWeights[i] += distribution.probability(myCities.get(i), myCities.get(j))/(myCities.get(i).distanceTo(myCities.get(j)));
				}
			}
			cityWeights[i]=cityWeights[i]*totalDistance;
		}
		for(i = 0;i<nbCities;i++) {
			meanCityWeight += cityWeights[i]/nbCities;
		}

		
		int tmp;
		
		for(j = 0; j < nbCities;j++) {
			bestCities[j] = j;
		}
		
		for(i = 0;i < nbCities-1;i++) {
			for(j = i; j < nbCities;j++) {
				if(cityWeights[j]<cityWeights[i]) {
					tmp = bestCities[i];
					bestCities[i] = bestCities[j];
					bestCities[j] = tmp;
				}
			}
		}
		
	}
	
	
	
	// UPDATE TABLES
	private void updateTables(Task task, int winner) {	
		int i;
		
		if(winner != agent.id()) {
			opponentNextActionTable = new int[opponentNextActionTable.length+2];
			
			for(i = 0; i<opponentNextActionTable.length;i++) {
				opponentNextActionTable[i] = opponentPotentialNextActionTable[i];
			}
			opponentPotentialNextActionTable = new int[opponentPotentialNextActionTable.length+2];
			opponentNbTasks++;
			
			myTasks.remove(myTasks.get(myTasks.size()-1));
			myDeliveryCities.remove(myDeliveryCities.get(myDeliveryCities.size()-1));
			myPickupCities.remove(myPickupCities.get(myPickupCities.size()-1));
		}
		else {
			nextActionTable = new int[nextActionTable.length+2];

			for(i = 0; i<nextActionTable.length;i++) {
				nextActionTable[i] = potentialNextActionTable[i];
			}
		
			potentialNextActionTable = new int[potentialNextActionTable.length+2];
			nbTasks++;
			
			opponentTasks.remove(opponentTasks.get(opponentTasks.size()-1));
			opponentDeliveryCities.remove(opponentDeliveryCities.get(opponentDeliveryCities.size()-1));
			opponentPickupCities.remove(opponentPickupCities.get(opponentPickupCities.size()-1));
		}
	}
	
	
	
	private void updatePotentialTables(Task task) {	
		int i;
		
		ArrayList<int[]> N = new ArrayList<int[]>();
		ArrayList<int[]> opponentN = new ArrayList<int[]>();
		myTasks.add(task);
		opponentTasks.add(task);
		myPickupCities.add(task.pickupCity);
		opponentPickupCities.add(task.pickupCity);
		myDeliveryCities.add(task.deliveryCity);
		opponentDeliveryCities.add(task.deliveryCity);
		
		N = addTask(nbTasks, myTasks,nextActionTable,nbTasks);
		opponentN = addTask(opponentNbTasks,opponentTasks,opponentNextActionTable,opponentNbTasks);
		potentialNextActionTable = localChoice(N, myPickupCities, myDeliveryCities,myTasks,false);
		opponentPotentialNextActionTable = localChoice(opponentN, opponentPickupCities,opponentDeliveryCities, opponentTasks,true);
		
		
	}
	
	
	
	
	// ADD TASK
	private ArrayList<int[]> addTask(int id, ArrayList<Task> myTasks, int[] actionTable, int nbTasks) {
		int i,j,k,l,tmp, tmp2;
		ArrayList<int[]> N = new ArrayList<int[]>();

		
		for(k = 0;k<nbVehicles;k++) {
			if(myTasks.get(id).weight < myVehicles.get(k).capacity()) {
				int newActionTable[] = new int[2*(nbTasks+1)+nbVehicles];
				
				for(l = 0;l<2*nbTasks;l++) {
		    		newActionTable[l] = actionTable[l];
		    		if(newActionTable[l] == 2*nbTasks)
		    			newActionTable[l]=2*(nbTasks+1);
		    	}
				for(l = 2*(nbTasks+1);l<2*(nbTasks+1)+nbVehicles;l++) {
		    		newActionTable[l] = actionTable[l-2];
		    		if(newActionTable[l] == 2*nbTasks)
		    			newActionTable[l]=2*(nbTasks+1);
				}
				tmp = newActionTable[2*nbTasks+2+k];
				newActionTable[2*(nbTasks+1)+k] = 2*id;
				newActionTable[2*id] = 2*id+1;
				newActionTable[2*id+1] = tmp;

				N.add(newActionTable);
				
				for(i = 0; i<2*(nbTasks+1)+nbVehicles-2;i++) {
					for(j = i+1; j<2*(nbTasks+1)+nbVehicles-1;j++ ) {
    			
						boolean validOrder = true;
						boolean validWeight = true;
						
						int newnewActionTable[] = new int[2*(nbTasks+1)+nbVehicles];
						
						for(l = 0;l<2*nbTasks;l++) {
				    		newnewActionTable[l] = actionTable[l];
				    		if(newnewActionTable[l] == 2*nbTasks)
				    			newnewActionTable[l]=2*(nbTasks+1);
				    	}
						for(l = 2*(nbTasks+1);l<2*(nbTasks+1)+nbVehicles;l++) {
				    		newnewActionTable[l] = actionTable[l-2];
				    		if(newnewActionTable[l] == 2*nbTasks)
				    			newnewActionTable[l]=2*(nbTasks+1);
						}

						tmp = newnewActionTable[2*nbTasks+2+k];
						newnewActionTable[2*(nbTasks+1)+k] = 2*id;
						newnewActionTable[2*id] = 2*id+1;
						newnewActionTable[2*id+1] = tmp;
    					
    					tmp = newnewActionTable[i];
    	    			newnewActionTable[i] = newnewActionTable[j];
    	    			tmp2 = newnewActionTable[j];
    	    			newnewActionTable[j] = newnewActionTable[tmp2];
    	    			newnewActionTable[tmp2] = tmp;

    	    			validOrder = controlOrder(newnewActionTable, nbTasks);
    	    			validWeight = controlWeight(k, myVehicles.get(k).capacity(), newnewActionTable, nbTasks, myTasks);
    	    			
    	    			if(validOrder && validWeight) {
    	    				N.add(newnewActionTable);
    	    			}

    				}
    			}
    		}
		}
		return N;
		
	}
	
	
	
	
	// MAKE PLAN
	private Plan makePlan(City current, int vehicleIndex, int[] actionTable, ArrayList<City> myPickupCities,ArrayList<City> myDeliveryCities,ArrayList<Task> myTasks) {
    	
//    	City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);
        int newNbTasks = (actionTable.length - nbVehicles)/2;
        
    	boolean exit = false;
    	int tmp;
    	tmp = actionTable[2*newNbTasks+vehicleIndex];
    	while(!exit) {
    		
    		if(tmp != 2*newNbTasks) {
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
	
	// LOCAL CHOICE
	private int[] localChoice(ArrayList<int[]> N, ArrayList<City> pickupCities,ArrayList<City> deliveryCities,ArrayList<Task> tasks, boolean opponent){
    	
    	int[] bestChoice = N.get(0);
    	ArrayList<Plan> firstPlans = new ArrayList<Plan>();
    	double cost = 0;
    	double newCost=0;
       	int i,j;

    	for(i = 0; i<myVehicles.size(); i++) {
    		if(!opponent)
    			firstPlans.add(makePlan(myVehicles.get(i).getCurrentCity(), i,N.get(0),pickupCities,deliveryCities,tasks));
    		else
    			firstPlans.add(makePlan(myCities.get(bestCities[0]), i,N.get(0),pickupCities,deliveryCities,tasks));
		}
    	
    	cost = calculateCost(firstPlans);
    
    	
    	for(j = 1; j< N.size();j++) {
    		ArrayList<Plan> plans = new ArrayList<Plan>();
    		
    		for(i = 0; i<nbVehicles; i++) {   
    			if(!opponent)
    				plans.add(makePlan(myVehicles.get(i).getCurrentCity(), i,N.get(j),pickupCities,deliveryCities,tasks));
    			else
    				plans.add(makePlan(myCities.get(bestCities[0]), i,N.get(j),pickupCities,deliveryCities,tasks));
    		} 
    		
    		newCost = calculateCost(plans);
    		
    		if(newCost <  cost ) {
    				cost = newCost;
    				bestChoice = N.get(j);
    		}

    	}
    	return bestChoice;   	
    }
	
	
	
	// CALCULATE COST
	private double calculateCost(List<Plan> plans) {
    	double myCost = 0;
    	int i;
    	for(i = 0;i < nbVehicles;i++) {
    		myCost += plans.get(i).totalDistance()*myVehicles.get(i).costPerKm();
    	}
    	return myCost;
    	
    }
	
	
	
	// CONTROL ORDER
	private boolean controlOrder(int[] actionTable, int tasks) {
    	boolean valid = true;
    	int newNbTasks = tasks+1;
    	
    	int controlTasks[] = new int[newNbTasks];
    	int i,k;

    	
    	
    	for(i = 0;i<2*newNbTasks;i++) {
			if(actionTable[i] == i)
				valid = false;
		}
    	for(i = 0; i<newNbTasks;i++) {
    		controlTasks[i] = -1;
    	}
    	
    	for(k = 0;k < nbVehicles;k++) {
    		
    		int idx = 2*newNbTasks+k;
    		int iter = 0;
    		
    		while(actionTable[idx] != 2*newNbTasks && valid) {
    			if(isPickup(actionTable[idx])) {
    				if(controlTasks[actionTable[idx]/2] != -1)
    					valid = false;
    				else
    					controlTasks[actionTable[idx]/2] = newNbTasks*(k+1);					
    			}
    			if(!isPickup(actionTable[idx])) {
    				if(controlTasks[actionTable[idx]/2] == 0 || iter == 0)
    					valid = false;
    				else
    					controlTasks[actionTable[idx]/2] -= newNbTasks*(k+1);
    			}   	
    			idx = actionTable[idx];
    			iter ++;
    		}
		}
		
		for(i = 0;i < newNbTasks;i++){
			if(controlTasks[i] != 0){
				valid = false;
			}
		} 
		return valid;
    }
	
	
	
	
	// CONTROL WEIGHT
	private boolean controlWeight(int vehicleNumber, int freeWeight, int[] actionTable, int nbTasks,ArrayList<Task> tasks) {
    	boolean valid = true;
    	int newNbTasks = nbTasks+1;
    	int idx = 2*newNbTasks+vehicleNumber;
    	
		while(actionTable[idx] != 2*newNbTasks && valid) {
			
			if(isPickup(actionTable[idx])) {
				freeWeight -= tasks.get(actionTable[idx]/2).weight;
				if(freeWeight < 0)
					valid = false;
			}
			else {
				freeWeight += tasks.get(actionTable[idx]/2).weight;
			}	
			
			idx = actionTable[idx];
		}
		return valid;
    }
	
	
	
	// IS PICKUP
	private boolean isPickup(int actionIndex) {
	    if(actionIndex%2== 1)
	    	return false;
	    else
	    	return true; 			
	}
	
}
