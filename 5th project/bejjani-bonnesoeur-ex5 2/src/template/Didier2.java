package template;

//the list of imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public class Didier2 implements AuctionBehavior {
	
	private SSGA alg = new SSGA();
	private Topology topology;
	private TaskDistribution distribution;
	private ArrayList<Task> wonTaskList = new ArrayList<Task>();
	private ArrayList<Task> auctionTaskList = new ArrayList<Task>();
	private Agent agent;
	private Random random;
	private List<Vehicle> vehicles;
	private HashMap<Coordination,Double> costMap = new HashMap<Coordination,Double>();
	private double previousCost=0;
	private double myCost = 0;
	
	private long mySeed = 0;
	
	private Coordination myCoord=null;
	private Coordination newCoord= null; 
	
	int nbGen = 0;
	int populationSize = 0;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

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
		//System.out.println("the winner is: "+winner);
		
		if (winner ==agent.id()) {
			wonTaskList.add(previous);
			if(newCoord!=null)
				myCoord = new Coordination(newCoord);
			else 
				myCoord = null;
			previousCost = newCoord.getTotalCost();
			myCost += bids[winner];
		}
		for (int i = 0;i< bids.length ;i++)
		{
			//System.out.println("tasks "+ wonTaskList.size() + " on a total of "+ auctionTaskList.size());
			//System.out.println("the bid of "+i+" is: "+bids[i]);
		}
	}
	
	@Override
	public Long askPrice(Task task) {
		
		auctionTaskList.add(task);
		ArrayList<Task> myTaskList = new ArrayList<Task>();// may be superficial copy
		myTaskList.addAll(wonTaskList);
		myTaskList.add(task);
		
		Coordination bestCoord = new Coordination(alg.generateCoord(vehicles,myTaskList,mySeed, populationSize, nbGen));
		newCoord = new Coordination(bestCoord);
		double cost = bestCoord.getTotalCost();
		costMap.put(bestCoord, cost);

		//previousCost = newCoord.getTotalCost();
		if (auctionTaskList.size()%5==0)
			return (long) Math.round(3*(cost/myTaskList.size()));
		else if (auctionTaskList.size()%3 == 0 )
			return (long) Math.round(1.5*(cost/myTaskList.size()));
		else if (auctionTaskList.size()%2==0)
			return (long) Math.round(2*(cost/myTaskList.size()));
		else 
			return (long) Math.round((cost/myTaskList.size()));


	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
		
		List<Plan> plans = new ArrayList<Plan>();
		System.out.println("----------------------------------------------");
		System.out.println("Agent  " + agent.id() + " have "+wonTaskList.size() + " tasks on "+ auctionTaskList.size());
		
		Coordination Xcoord = new Coordination (alg.generateCoord(vehicles, wonTaskList, mySeed,populationSize, nbGen));
		System.out.println( " previous Cost " + previousCost +" Actual Cost "+ Xcoord.getTotalCost());
		
		System.out.println("the reward of agent "+agent.id()+" is: "+(myCost-Xcoord.getTotalCost()));
		plans.addAll(Xcoord.getPlans());
		//System.out.println( " After Plan" + plans);

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
