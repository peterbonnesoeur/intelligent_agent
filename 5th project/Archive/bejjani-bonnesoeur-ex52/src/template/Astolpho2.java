package template;

//the list of imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.Iterator;
import java.util.TreeMap;

//import ilog.concert.IloIntNaryTable.Iterator;

import java.util.Random;

import template.Coordination;
import template.SSGA1;
import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
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
 */
@SuppressWarnings("unused")
public class Astolpho2 implements AuctionBehavior {

	private SSGA1 alg = new SSGA1();
	private Topology topology;
	private TaskDistribution distribution;
	private ArrayList<Task> wonTaskList = new ArrayList<Task>();
	private ArrayList<Task> lostTaskList = new ArrayList<Task>();
	private ArrayList<Task> auctionTaskList = new ArrayList<Task>();
	private Agent agent;
	private Random random;
	private List<Vehicle> vehicles;
	
	
	private ArrayList<Long> listOfMyCost = new ArrayList<Long>();
	private ArrayList<Long> listOfEnemyCost = new ArrayList<Long>();
	private ArrayList<Long> listOfEnemyBid = new ArrayList<Long>();

	private ArrayList<Double> errors = new ArrayList<Double>();

	private double previousCost = 0;
	private double previousCostEnemy = 0;
	private double myReward = 0;

	private long mySeed = 0;
	
	private double error = 0.0;

	private Coordination newCoord = null;
	private Coordination newCoordEnemy = null;

	
    private long timeout_plan;
    private long timeout_bid;

	int nbGen = 0;
	int populationSize = 0;

	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();

		long seed = -9019554669489983951L * vehicles.hashCode() * agent.id();
		this.random = new Random(seed);
		this.mySeed = seed;

		nbGen = agent.readProperty("nbGen", Integer.class, 100);
		populationSize = agent.readProperty("populationSize", Integer.class, 20);
		
		

		LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		 timeout_bid = (long) Math.round((((double)ls.get(LogistSettings.TimeoutKey.BID)-0.01*(double)ls.get(LogistSettings.TimeoutKey.BID))));
	     timeout_plan = (long) Math.round((((double)ls.get(LogistSettings.TimeoutKey.PLAN)-0.01*(double)ls.get(LogistSettings.TimeoutKey.PLAN))));
		System.out.println("timeout plan :"+ timeout_plan );
		System.out.println("timeout bid :"+ timeout_bid );

	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		// Gives the results of the auction
		
		long min = Long.MAX_VALUE;
		
		System.out.println("the winner is: " + winner);


		if (winner == agent.id()) {
			wonTaskList.add(previous);
			previousCost = newCoord.getTotalCost();
			myReward += bids[winner];
		}else {
			lostTaskList.add(previous);
			previousCostEnemy = newCoordEnemy.getTotalCost();
		}
		
		
		for (int i = 0; i < bids.length; i++) {
			if (i != agent.id() && min > bids[i])
				min = bids[i];
			System.out.println("the bid of " + i + " is: " + bids[i]);

		}
		
		
		if (listOfEnemyBid.get(listOfEnemyBid.size()-1)!=0) {
			error = ((double)listOfEnemyBid.get(listOfEnemyBid.size()-1)-(double)min)/((double)listOfEnemyBid.get(listOfEnemyBid.size()-1));
			errors.add(error);
		}
		
		System.out.println("error"+error);

		listOfEnemyBid.remove(listOfEnemyBid.size()-1);
		
		error = 0;
		
		if (errors.size()<6) {
			for (int i = 0 ; i< errors.size();i++)
				error += errors.get(i);
			
			error/= errors.size();
		}
		else {
			for (int i = 0 ; i< 5;i++)
				error += errors.get(errors.size()-1-i);
			
			error/= 6;
		}
		
		listOfEnemyBid.add(min);
	}

	@Override
	public Long askPrice(Task task) {
		
		// asks the bid of the agent for each task

		auctionTaskList.add(task);
		ArrayList<Task> myTaskList = new ArrayList<Task>();
		myTaskList.addAll(wonTaskList);
		myTaskList.add(task);

		//	Generate the cost needed to achieve the task
		
		// generate our path with a genetic algorithm
		newCoord = new Coordination(alg.generateCoord(vehicles, myTaskList, mySeed, populationSize, (long)Math.round(0.45*timeout_bid)));
		

		double cost = costCalculator(newCoord, myTaskList.size(),previousCost);	//Calculate the cost with an estimation of the other possible paths
		
		listOfMyCost.add((long) cost);
		
		
		//	Generate an estimation of the cost needed for the enemy to achieve the task
		
		myTaskList.clear();
		myTaskList.addAll(lostTaskList);
		myTaskList.add(task);
		
		newCoordEnemy = new Coordination(alg.generateCoord(vehicles, myTaskList, mySeed, populationSize, (long)Math.round(0.4*timeout_bid)));
		

		double costEnemy = costCalculator(newCoordEnemy, myTaskList.size(),previousCostEnemy);	//Calculate the cost with an estimation of the other possible paths
		
		listOfEnemyCost.add((long) costEnemy);
		
		
		
		double meanEnemy = 0;
		
		if (listOfEnemyBid.size()<7) {
			for (int i = 0;  i< listOfEnemyBid.size();i++) {
				meanEnemy += listOfEnemyBid.get(i);
			}
			meanEnemy/=listOfEnemyBid.size();
		
		}else {
			for (int i = 0;  i< 6;i++) {
				meanEnemy += listOfEnemyBid.get(listOfEnemyBid.size()-1-i);
			}
			meanEnemy/=7;
		}
		
		
		if (listOfEnemyBid.size()==0)
			listOfEnemyBid.add((long) (costEnemy*1.05));
		else {

			if (error>0)
				listOfEnemyBid.add((long)Math.round(((double)meanEnemy)*(1-0.2)));
			else
				listOfEnemyBid.add((long)Math.round(((double)meanEnemy)*(1+0.2)));
			
				
			
		}
	
		
		double bid = 0;
		if (Math.abs(error)<0.3) {
			bid = cost + Math.abs(costEnemy/cost)*rewardGenerator();
			
		}
		else 
			bid = cost + rewardGenerator2();	
		
		
		System.out.println("new cost " +bid +" vs " + cost);
		System.out.println("Cost per Task " + (newCoord.getTotalCost()- previousCost) );
		
		
		if (bid < 1.05 * Math.abs(cost)) {
			return (long) Math.round(1.05 * cost);
		}
		
		if (bid>task.reward) 
			return task.reward;

		return (long) bid;
	}
	
	

	
	
	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
		// Generates the plan

		List<Plan> plans = new ArrayList<Plan>();
		System.out.println("----------------------------------------------");
		System.out.println(
				"Agent  " + agent.id() + " have " + wonTaskList.size() + " tasks on " + auctionTaskList.size());

		if (wonTaskList.size() != 0) {
			Coordination Xcoord = new Coordination(alg.generateCoord(vehicles, wonTaskList, mySeed, populationSize,(long)Math.round(0.9*timeout_plan)));
			System.out.println("the reward of agent " + agent.id() + " is: " + (myReward - Xcoord.getTotalCost()));
			plans.addAll(Xcoord.getPlans());
			}
		else {
			for (int i=0; i<vehicles.size();i++) {
				plans.add(Plan.EMPTY);
			}
		}
		

		return plans;

	}

	
	/*************************Functions *********************************************/
	
	
	private double costCalculator(Coordination bestCoord,int size,double previousCost) {
		
		// Calculates the cost while considering the future tasks around the last position 
		
		ArrayList<City> LastCities = new ArrayList<City>(bestCoord.getLastCity());
		double totalWeight = 1, totalCost = bestCoord.getTotalCost()-previousCost;
		
		
		for (int i = 0; i<LastCities.size();i++) {
			TreeMap<Double, Task> probTasks = new TreeMap<Double, Task>();
			
			if (LastCities.get(i) != null) {
				for (City f : LastCities.get(i).neighbors()) {
					for (City t : topology) {
						Task myTask = new Task(-1, f,t,distribution.reward(f, t),distribution.weight(f, t)) ;
						probTasks.put(distribution.probability(f, t),myTask);
					}
				}
				
				for (City t : topology) {
					Task myTask = new Task(-1, LastCities.get(i),t,distribution.reward(LastCities.get(i), t),distribution.weight(LastCities.get(i), t)) ;
					probTasks.put(distribution.probability(LastCities.get(i), t),myTask);
				}
				
				
				NavigableMap<Double, Task> nmap = probTasks.descendingMap();
				
				Set<Entry<Double, Task>> set = nmap.entrySet();
			    int nbpath = 0;
			    Iterator<Entry<Double, Task>> index = set.iterator();
			    
			    while(index.hasNext()&& nbpath<5 ) {
			      nbpath++;
			      Map.Entry <Double, Task> me = (Entry<Double, Task>)index.next();
			      
			      Coordination myCoord = new Coordination(bestCoord);
			      
			      myCoord.addTaskToSequence( me.getValue(),i);
			      totalWeight = totalWeight + me.getKey(); 
			      totalCost+= ( me.getKey())* (myCoord.getTotalCost()-previousCost)/2;
			    }
			}
		}
		
		return totalCost/totalWeight;
	}
	
	

/******************************Generation of the reward **********************************************/

	private double rewardGenerator() {		//generates the reward
		
		if (!listOfEnemyBid.isEmpty()) {
			double mean = 0;
			double dynamicVar = dynamicSigma();
			double sign = Math.signum(dynamicVar);
			dynamicVar = Math.abs(dynamicVar);
			
			ArrayList<Double> myWeight = new ArrayList<Double>(
					WeightGenerator(listOfEnemyBid.size(), sign*Math.sqrt(dynamicVar), listOfEnemyBid.size()));
	
			for (int i = 0; i < listOfEnemyBid.size(); i++) {
				mean += (-listOfEnemyCost.get(i) + listOfEnemyBid.get(i)) * myWeight.get(listOfEnemyBid.size() - 1 - i);
			}
	
			return mean;
		}
		else
			return 0.0;
	}
	
private double rewardGenerator2() {		//generates the reward
		
		if (!listOfEnemyBid.isEmpty()) {
			double mean = 0;
			double dynamicVar = dynamicSigma();
			double sign = Math.signum(dynamicVar);
			dynamicVar = Math.abs(dynamicVar);
			
			ArrayList<Double> myWeight = new ArrayList<Double>(
					WeightGenerator(listOfEnemyBid.size(), sign*Math.sqrt(dynamicVar), listOfEnemyBid.size()));
	
			for (int i = 0; i < listOfEnemyBid.size(); i++) {
				mean += (-listOfMyCost.get(i) + listOfEnemyBid.get(i)) * myWeight.get(listOfEnemyBid.size() - 1 - i);
			}
	
			return mean;
		}
		else
			return 0.0;
	}
	
	private ArrayList<Double> WeightGenerator(int size, double sigma, double shift) {
		
		// Gives a Weight to each value depending on how the bid of the enemy is changing

		System.out.println("Sigma  " + sigma);
		System.out.println("Shift  " + shift);
		ArrayList<Double> myWeight = new ArrayList<Double>();
		double total = 0;
		if (size ==1) {
			 myWeight.add(1.0);
			 return myWeight;
		}
		for (int i = 0; i < size; i++) {
			myWeight.add(1 - 1 / (double) (1 + Math.exp(-sigma * ((double) i - shift))));
			total += myWeight.get(i);
		}
		for (int i = 0; i < size; i++)
			myWeight.set(i, myWeight.get(i) / total);
		return myWeight;
	}

	private Double dynamicSigma() {			//Create a coefficient to estimate the Weight for each value in the function rewardGeneration

		double meanOfEnemy = 0;
		double variance = 0;
		int size = listOfEnemyBid.size();
		for (int i = 0; i < listOfEnemyBid.size(); i++) {
			meanOfEnemy += listOfEnemyBid.get(i);
		}
		meanOfEnemy /= size;

		for (int i = 0; i < listOfEnemyBid.size(); i++) {
			variance += Math.pow((meanOfEnemy - listOfEnemyBid.get(i)), 2);
		}

		variance /= (size);

		return variance / meanOfEnemy;
	}
	
	
	
	
}
