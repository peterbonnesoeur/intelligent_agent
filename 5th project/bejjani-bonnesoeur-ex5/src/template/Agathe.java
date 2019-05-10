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
import template.SSGA;

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
 */
@SuppressWarnings("unused")
public class Agathe implements AuctionBehavior {

	private SSGA alg = new SSGA();
	private Topology topology;
	private TaskDistribution distribution;
	private ArrayList<Task> wonTaskList = new ArrayList<Task>();
	private ArrayList<Task> auctionTaskList = new ArrayList<Task>();
	private Agent agent;
	private Random random;
	private List<Vehicle> vehicles;
	private ArrayList<Long> listOfMyCost = new ArrayList<Long>();
	private ArrayList<Long> listOfEnemyBid = new ArrayList<Long>();

	// private HashMap<Coordination,Double> costMap = new
	// HashMap<Coordination,Double>();

	private double previousCost = 0;
	private double myCost = 0;

	private long mySeed = 0;

	private Coordination myCoord = null;
	private Coordination newCoord = null;

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
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		System.out.println("the winner is: " + winner);
		long min = Long.MAX_VALUE;

		if (winner == agent.id()) {
			wonTaskList.add(previous);
			if (newCoord != null)
				myCoord = new Coordination(newCoord);
			else
				myCoord = null;
			previousCost = newCoord.getTotalCost();
			myCost += bids[winner];
		}
		for (int i = 0; i < bids.length; i++) {
			if (i != agent.id() && min > bids[i])
				min = bids[i];
			// System.out.println("tasks "+ wonTaskList.size() + " on a total of "+
			// auctionTaskList.size());
			System.out.println("the bid of " + i + " is: " + bids[i]);
		}
//		if (max<previous.reward && max> listOfMyCost.get(listOfMyCost.size()-1))
//			listOfEnemyBid.add(max);
//		else 
//			listOfMyCost.remove(listOfMyCost.size()-1);
		listOfEnemyBid.add(min);
	}

	@Override
	public Long askPrice(Task task) {

		auctionTaskList.add(task);
		ArrayList<Task> myTaskList = new ArrayList<Task>();// may be superficial copy
		myTaskList.addAll(wonTaskList);
		myTaskList.add(task);

		Coordination bestCoord = new Coordination(
		alg.generateCoord(vehicles, myTaskList, mySeed, populationSize, nbGen));
		newCoord = new Coordination(bestCoord);
		

//		double cost = costCalculator(bestCoord, myTaskList.size());
//		listOfMyCost.add((long) cost);
//		
//		
//
//		cost += rewardGen(cost, task);
//
//		// costMap.put(bestCoord, cost);
//
//		// previousCost = newCoord.getTotalCost();
//		
//		
//	
//		
//		if (cost < 1.05 * (bestCoord.getTotalCost() / myTaskList.size())) {
//			return (long) Math.round(1.05 * bestCoord.getTotalCost() / myTaskList.size());
//		}
//		
//		if (cost>task.reward)
//			cost = task.reward;
//
//		return (long) Math.round(0.95 * cost);
//
//		
		
//		cost /= myTaskList.size();
		double cost = costCalculator2(myTaskList,bestCoord);
		//double cost = costCalculator(bestCoord, myTaskList.size());
		
		System.out.println("old cost " +cost);
		
		listOfMyCost.add((long) cost);

		

		
		double bid = cost + rewardGen(cost, task);
		System.out.println("new cost " +bid +" vs " + 1.05 * (bestCoord.getTotalCost() / myTaskList.size()));

		// costMap.put(bestCoord, cost);

		// previousCost = newCoord.getTotalCost();
		

		
		if (bid < 1.05 * cost) {
			return (long) Math.round(1.05 * cost);
		}
		
//		if (bid>task.reward) 
//			return task.reward;

		return (long) Math.round(0.95 * bid);
	}

	private double costCalculator2(ArrayList<Task> taskList, Coordination myCoord ) {
		
		TreeMap<Double, Task> probTasks = new TreeMap<Double, Task>();
		
		for (City f : topology) {
			for (City t : topology) {
				if (t!=f) {
					Task myTask = new Task(-1, f,t,distribution.reward(f, t),distribution.weight(f, t)) ;
					probTasks.put(distribution.probability(f, t),myTask);
				}
			}
		}
		
		NavigableMap<Double, Task> nmap = probTasks.descendingMap();
		//NavigableSet<Double> A =  probTasks.descendingKeySet();
		
		Set<Entry<Double, Task>> set = nmap.entrySet();
	    int nbpath = 0;
	    Iterator<Entry<Double, Task>> index = set.iterator();
	    double totalWeight = 1, totalCost = myCoord.getTotalCost()/taskList.size();
	    
		while(index.hasNext()&& nbpath<2 ) {
		      
			  nbpath++;
		      Map.Entry <Double, Task> me = (Entry<Double, Task>) index.next();
		      taskList.add(me.getValue());
		      Coordination newCoord = new Coordination (vehicles, taskList, mySeed);
		      System.out.println("Proba is : " + me.getKey()+"for task : "+ me.getValue() );
		      totalWeight = totalWeight + me.getKey(); 
		      totalCost+= ( me.getKey())* newCoord.getTotalCost()/taskList.size();
		      
		      taskList.remove(taskList.size()-1);
		}
		
		return totalCost/totalWeight;	
	}
	
	
	
	private double costCalculator(Coordination bestCoord,int size) {
		
		ArrayList<City> LastCities = new ArrayList<City>(bestCoord.getLastCity());
		double totalWeight = 1, totalCost = bestCoord.getTotalCost()/size;
		
		
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
				//NavigableSet<Double> A =  probTasks.descendingKeySet();
				
				Set<Entry<Double, Task>> set = nmap.entrySet();
			    int nbpath = 0;
			    Iterator<Entry<Double, Task>> index = set.iterator();
			    
			    while(index.hasNext()&& nbpath<5 ) {
			      nbpath++;
			      Map.Entry <Double, Task> me = (Entry<Double, Task>)index.next();
			      
			      Coordination myCoord = new Coordination(bestCoord);
			      
			      myCoord.addTaskToSequence( me.getValue(),i);
			      totalWeight = totalWeight + me.getKey(); 
			      totalCost+= ( me.getKey())* myCoord.getTotalCost()/(size+1);
			    }
			}
		}
		
		return totalCost/totalWeight;
	}
	
	private Double dynamicVariance() {

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

		
		System.out.println("Variance : " + variance);

		return variance / meanOfEnemy;
	}

	private Double rewardGen(double cost, Task task) {
		System.out.println("tasks ");
		
		if (!listOfEnemyBid.isEmpty()) {
			return (Math.abs(MeanGenerator()));

		} else
			return 0.0;
	}

	private double MeanGenerator() {
		double mean = 0;
		double dynamicVar = dynamicVariance();
		double sign = Math.signum(dynamicVar);
		dynamicVar = Math.abs(dynamicVar);
		
		ArrayList<Double> myWeight = new ArrayList<Double>(
				WeightedMean(listOfEnemyBid.size(), sign*Math.sqrt(dynamicVar), sign*Math.sqrt(Math.abs(dynamicVar)/2)));//5.0));//Math.sqrt(listOfEnemyBid.size()/dynamicVariance()/2)));

		for (int i = 0; i < listOfEnemyBid.size(); i++) {
			mean += (-listOfMyCost.get(i) + listOfEnemyBid.get(i)) * myWeight.get(listOfEnemyBid.size() - 1 - i);
		}

		return mean;
	}

	private ArrayList<Double> WeightedMean(int size, double sigma, double shift) {

		System.out.println("Sigma  " + sigma);
		System.out.println("Shift  " + shift);
		ArrayList<Double> myWeight = new ArrayList<Double>();
		double total = 0;
		for (int i = 0; i < size; i++) {
			myWeight.add(1 - 1 / (double) (1 + Math.exp(-sigma * ((double) i - shift))));
			total += myWeight.get(i);
		}
		for (int i = 0; i < size; i++)
			myWeight.set(i, myWeight.get(i) / total);
		return myWeight;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		List<Plan> plans = new ArrayList<Plan>();
		System.out.println("----------------------------------------------");
		System.out.println(
				"Agent  " + agent.id() + " have " + wonTaskList.size() + " tasks on " + auctionTaskList.size());

		Coordination Xcoord = new Coordination(alg.generateCoord(vehicles, wonTaskList, mySeed, 4*populationSize, 3*nbGen));
		System.out.println(" previous Cost " + previousCost + " Actual Cost " + Xcoord.getTotalCost());

		System.out.println("the reward of agent " + agent.id() + " is: " + (myCost - Xcoord.getTotalCost()));
		plans.addAll(Xcoord.getPlans());
		// System.out.println( " After Plan" + plans);

		return plans;

	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicles.get(0).getCurrentCity();
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
}
