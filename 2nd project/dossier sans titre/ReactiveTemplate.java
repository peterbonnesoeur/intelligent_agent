package template;

import java.util.Random;
import java.util.ArrayList;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveTemplate implements ReactiveBehavior {

	private static final double ERROR = 0.3;
	
	private Random random;
	private double pPickup;
	private int numActions;
	private double discount;
	private Topology topo;
	private TaskDistribution dist;
	private Agent myAgent;
	private ArrayList<Double> V =new ArrayList<Double>();
	private Boolean setupTable   = false;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		discount = agent.readProperty("discount-factor", Double.class, 0.95);
		
		topo= topology;
		dist = td;

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
	}
	
	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		
		
		if (!setupTable){
			
			setupTable = true;
			RLA(discount, topo, dist,vehicle.costPerKm());
		}
		
		Action action;

		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	
	
	public void RLA(double discount, Topology topology, TaskDistribution td, int cost) {
		
		double V_p[] = new double [topology.size()];
		double Q[] = new double [topology.size()];
		double maxError;
		
		for (int i = 0; i < topology.size(); i++) {
	        V_p[i] = Math.random();
	    }
		
		do {
			maxError = 0;
			
			for(City fromCity:topology) {
				for(City toCity:topology) {
					if(fromCity.id!=toCity.id) {
						Q[toCity.id]= td.reward(fromCity, toCity) - cost*fromCity.distanceTo(toCity);
						for(City futureCity:topology)
						{
							if(futureCity.id!=toCity.id)
							{
								Q[toCity.id]+=discount*V_p[futureCity.id]*td.probability (toCity, futureCity) ;
							}
						}
					}
					if(V.get(fromCity.id)< Q[toCity.id]) {
						V.set(fromCity.id,Q[toCity.id]);
					}
				}
				if (maxError<Math.abs(V_p[fromCity.id]-V.get(fromCity.id)))
					maxError = Math.abs(V_p[fromCity.id]-V.get(fromCity.id));
				
			}
			for (int i = 0 ; i>topology.size();i++)
				V_p[i]=V.get(i);
		
		}while(maxError>=ERROR);
	}
	
}
