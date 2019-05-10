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

	private static final double ERROR = 0.001;
	
	private Random random;
	private double pPickup;
	private int numActions;
	private Topology topo;
	private TaskDistribution dist;
	private Agent myAgent;
	private ArrayList<Double> V =new ArrayList<Double>();
	private Boolean setupTable   = false;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		double discount = agent.readProperty("discount-factor", Double.class, 0.95);
		
		topo= topology;
		dist = td;

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
		System.out.println(discount );
	}
	
	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		
		
		// *********************************Compute the behavior table********************************//
		
		if (!setupTable && !this.myAgent.name().equals("reactive-random")){
			setupTable = true;
			RLA(vehicle.costPerKm());
		}
		
		
		Action action;
		
		
		// ******************Choose if the vehicle pick the task or go to another city ********************************//
		
		
		
		if (this.myAgent.name().equals("reactive-dummy"))
		{
			double min = vehicle.getCurrentCity().distanceTo(vehicle.getCurrentCity().randomNeighbor(random)) ;
			City nearCity = vehicle.getCurrentCity().randomNeighbor(random);
			
			for(City myCity: vehicle.getCurrentCity()) {
				if( vehicle.getCurrentCity().distanceTo(myCity)<=min)
				{
					min = vehicle.getCurrentCity().distanceTo(myCity);
					nearCity = myCity;
				}
			}
			
			if (availableTask != null && nearCity.id ==availableTask.deliveryCity.id)
				action = new Pickup(availableTask);
			else
				action = new Move(nearCity);
		}
		
		
		
		if(!this.myAgent.name().equals("reactive-random"))					//reinforcement learning 
		{
			City id = ChooseAction(availableTask, vehicle.getCurrentCity() , vehicle.costPerKm());
			
			if (availableTask == null || id != availableTask.deliveryCity ) 
				action = new Move(id);
			else 
				action = new Pickup(availableTask);
			
		} else 														//randomized actions
		{
			
			if (availableTask == null || random.nextDouble() > pPickup) {
				City currentCity = vehicle.getCurrentCity();
				action = new Move(currentCity.randomNeighbor(random));
			} else 
				action = new Pickup(availableTask);
		}
		
		
		if (numActions >= 1) {
			
			System.out.println("The total of profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	
	public City ChooseAction(Task availableTask, City CurrentCity, int cost) {
		
		double  Pick=0;
		double 	Go = 0;
		double 	previousGo =0;
		City id = CurrentCity.randomNeighbor(random);
		
		
		// ***************Compute the expected reward if the task is picked up ****************//
		if (availableTask!= null) 
		{
			Pick = availableTask.reward - cost*CurrentCity.distanceTo(availableTask.deliveryCity);
			
			for(City futureCity:availableTask.deliveryCity)
			{
					Pick+=this.pPickup* V.get(futureCity.id)*dist.probability (availableTask.deliveryCity, futureCity);
			}
		}
		
		//*******************Compute the expected reward if there is no task or if the task is not picked up*********************//
		
		for(City toCity: CurrentCity) 
		{
				Go-= cost*CurrentCity.distanceTo(toCity);
				for(City futureCity:toCity)
				{
					Go+=this.pPickup*V.get(futureCity.id)*dist.probability (toCity, futureCity);
				}
			
			if (Go>previousGo) 
			{
				previousGo = Go;
				id= toCity;
			}
		}
	
		if (Go>Pick || availableTask== null)
			return id;
		else 
			return availableTask.deliveryCity;	
	}
	
	
	public void RLA(int cost) 
	{
		
	
		
		double V_p[] = new double [topo.size()];
		double Q[] = new double [topo.size()];
		double maxError;
		
		for (int i = 0; i < topo.size(); i++) 
		{
	        V_p[i] = Math.random();
	        V.add(Math.random());
	    }
		
		
		int b = 0;
		
		do 
		{
			maxError = 0;
			
			for(City fromCity:topo) 
			{
				for(City toCity:fromCity) 
				{
					Q[toCity.id]= dist.reward(fromCity, toCity) - cost*fromCity.distanceTo(toCity);
					for(City futureCity:toCity)
					{
						
							Q[toCity.id]+= this.pPickup*V_p[futureCity.id]*dist.probability (toCity, futureCity);
					}
					if(V.get(fromCity.id)< Q[toCity.id]) 
						V.set(fromCity.id,Q[toCity.id]);
					
				}
				if (maxError<Math.abs(V_p[fromCity.id]-V.get(fromCity.id)))
					maxError = Math.abs(V_p[fromCity.id]-V.get(fromCity.id));
			}
			for (int i = 0 ; i<topo.size();i++) 
				V_p[i]=V.get(i);
			
			b++;
			
			System.out.println("loop n°" + b +" erreur" + maxError );
		
		}while(maxError>=ERROR);
	}
	
}


