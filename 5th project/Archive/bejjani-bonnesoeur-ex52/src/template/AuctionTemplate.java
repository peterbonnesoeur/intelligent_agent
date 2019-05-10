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
public class AuctionTemplate implements AuctionBehavior {

	private SSGA1 alg = new SSGA1();
	private Topology topology;
	private TaskDistribution distribution;
	private ArrayList<Task> wonTaskList = new ArrayList<Task>();
	private ArrayList<Task> auctionTaskList = new ArrayList<Task>();
	private Agent agent;
	private Random random;
	private List<Vehicle> vehicles;
	private ArrayList<Long> listofMyBid = new ArrayList<Long>();
	private ArrayList<Long> listOfEnemyBid = new ArrayList<Long>();

	// private HashMap<Coordination,Double> costMap = new
	// HashMap<Coordination,Double>();

	private double previousCost = 0;
	private double myCost = 0;

	private long mySeed = 0;

	private Coordination myCoord = null;
	private Coordination newCoord = null;
	
    private long timeout_plan;
    private long timeout_bid;

	int nbGen = 0;
	int populationSize = 0;
	
	
	private double mySigma = -Double.MIN_VALUE;
	
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
		
		System.out.println("before timeout " );

		LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
		
		 timeout_bid = ls.get(LogistSettings.TimeoutKey.BID)/10;
	        // the plan method cannot execute more than timeout_plan milliseconds
	        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN)/100;
			System.out.println("after timeout "+ timeout_plan );

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
			previousCost = bids[winner];
			myCost += bids[winner];
		}
		for (int i = 0; i < bids.length; i++) {
			if (i != agent.id() && min > bids[i])
				min = bids[i];
			// System.out.println("tasks "+ wonTaskList.size() + " on a total of "+
			// auctionTaskList.size());
			System.out.println("the bid of " + i + " is: " + bids[i]);
		}

		listOfEnemyBid.add(min);
	}

	@Override
	public Long askPrice(Task task) {

		auctionTaskList.add(task);
		ArrayList<Task> myTaskList = new ArrayList<Task>();// may be superficial copy
		myTaskList.addAll(wonTaskList);
		myTaskList.add(task);

		
		Coordination bestCoord = new Coordination(
				alg.generateCoord(vehicles, myTaskList, mySeed, populationSize, (long)Math.round(0.9*timeout_bid)));
		newCoord = new Coordination(bestCoord);
		

	
		
//		cost /= myTaskList.size();
		//double cost = costCalculator2(myTaskList,bestCoord);
		double cost = costCalculator(bestCoord, myTaskList.size());
		
		System.out.println("old cost " +cost);
		
		

		

		
		double bid = cost + rewardGen(cost, task);
		System.out.println("new cost " +bid +" vs " + cost);
		System.out.println("Cost per Task " + (bestCoord.getTotalCost()- previousCost) );

		
		listofMyBid.add((long) cost);
		// costMap.put(bestCoord, cost);

		// previousCost = newCoord.getTotalCost();
		

		
//		if (bid < 1.05 * cost) {
//			return (long) Math.round(1.05 * cost);
//		}
		
		if (bid>task.reward) 
			return task.reward;

		return (long) Math.round(0.95 * bid);
	}


	
	
	private double costCalculator(Coordination bestCoord,int size) {
		
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
			      totalCost+= ( me.getKey())* (myCoord.getTotalCost()-previousCost)/2;
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
			return ((MeanGenerator()));

		} else
			return 0.0;
	}

	private double MeanGenerator() {
		double mean = 0,variance = 0;
		double dynamicVar = dynamicVariance();
		double sign = Math.signum(dynamicVar);
		dynamicVar = Math.abs(dynamicVar);
		
		ArrayList<Double> myWeight = new ArrayList<Double>(
				WeightedMean(listOfEnemyBid.size(), sign*Math.sqrt(dynamicVar)/10, listOfEnemyBid.size()));//sign*Math.sqrt(Math.abs(dynamicVar)/2)));//5.0));//Math.sqrt(listOfEnemyBid.size()/dynamicVariance()/2)));

		for (int i = 0; i < listOfEnemyBid.size(); i++) {
			mean += (-listofMyBid.get(i) + listOfEnemyBid.get(i)) * myWeight.get(listOfEnemyBid.size() - 1 - i);
		}

		for (int i = 0; i < listOfEnemyBid.size(); i++) {
			variance += Math.pow((mean - (-listofMyBid.get(i) + listOfEnemyBid.get(i))*myWeight.get(listOfEnemyBid.size()-1-i)), 2);
		}
		variance /=listOfEnemyBid.size();
		variance /= mean;
		
		System.out.println("	Mean : " + mean);

		System.out.println("	Variance : " + variance);

		return (mean);//+ 1/Math.sqrt(2*Math.PI)*Math.exp((2*random.nextDouble()-1.0)/2)*Math.sqrt(Math.abs(variance)));
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

		Coordination Xcoord = new Coordination(alg.generateCoord(vehicles, wonTaskList, mySeed, populationSize,  (long)Math.round(0.9*timeout_plan)));
		System.out.println(" previous Cost " + previousCost + " Actual Cost " + Xcoord.getTotalCost());

		System.out.println("the reward of agent " + agent.id() + " is: " + (myCost - Xcoord.getTotalCost()));
		plans.addAll(Xcoord.getPlans());
		// System.out.println( " After Plan" + plans);

		return plans;

	}

	
}