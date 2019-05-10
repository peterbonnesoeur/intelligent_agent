package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import logist.task.Task;
import logist.simulation.Vehicle;
import template.Coordination;

public class SSGA{
	
	
	
	private Random rand;
	

	
	public Coordination generateCoord(List<Vehicle> vehicles, ArrayList<Task> tasks, long seed,int pop,long bidTimeOut) 
    {
		//System.out.println("we had a initial cost of");
		this.rand = new Random(seed);
		int populationSize = pop;//20;
		long initTime = System.currentTimeMillis();//100;
		
		double mutationRate = 1.0;
		
		Map<Coordination,Double> populationProb = new HashMap<Coordination,Double>();
		Map<Coordination,Double> populationCost = new HashMap<Coordination,Double>();
		
		//System.out.println("we had a initial cost of "+ new Coordination(vehicles,tasks,seed).getTotalCost());

		
		for(int i=0;i<populationSize;i++)
		{
			Coordination ind = new Coordination(vehicles,tasks,seed);
			double indCost = ind.getTotalCost();
			populationProb.put(ind,1/indCost);
			populationCost.put(ind,indCost);
		}
		
		Distribution<Coordination> mutateDist = new Distribution<Coordination>(populationProb);
		
		while((System.currentTimeMillis()-initTime)<bidTimeOut)
		{
			for(int j=0;j<populationSize;j++)
			{
				//Coordination bestCoord = getBestInd(populationCost, mutateDist);
				Coordination parent = mutateDist.sample();
				Coordination mutant = parent.mutate(mutationRate);
				
				
				if(parent.getTotalCost()>mutant.getTotalCost())
				{
					Coordination toDie = getWorstInd(populationCost, mutateDist);
					populationProb.put(mutant, 1/mutant.getTotalCost());
					populationProb.remove(toDie);
					
					populationCost.put(mutant, mutant.getTotalCost());
					populationCost.remove(toDie);
				}
				mutateDist = new Distribution<Coordination>(populationProb);
				
			}
			
		}
		Coordination bestCoord = getBestInd(populationCost, mutateDist);
		
		return bestCoord;
	}

    
	private Coordination getBestInd(Map<Coordination,Double> populationCost, Distribution<Coordination> mutateDist) 
	{
		Coordination bestCoord = mutateDist.sample();
		for(Coordination coord:populationCost.keySet())
		{
			if(coord.getTotalCost()<bestCoord.getTotalCost())
			{
				bestCoord = coord;
			}
		}
		return bestCoord;
	}
	
		
	private Coordination getWorstInd(Map<Coordination,Double> populationCost, Distribution<Coordination> mutateDist) 
	{
		Coordination worstCoord = mutateDist.sample();
		for(Coordination coord:populationCost.keySet())
		{
			if(coord.getTotalCost()>worstCoord.getTotalCost())
			{
				worstCoord = coord;
			}
		}
		return worstCoord;
	}
	
	public class Distribution<T>{
	    List<Double> probs = new ArrayList<Double>();
	    List<T> events = new ArrayList<T>();
	    double sumProb;
	
	    Distribution(Map<T,Double> probs)
	    {
	        for(T event : probs.keySet())
	        {
	            sumProb += probs.get(event);
	            events.add(event);
	            this.probs.add(probs.get(event));
	        }
	    }

	    public T sample()
	    {
	       // T value;
	        double prob = rand.nextDouble()*sumProb;
	        int i;
	        
	        for(i=0; prob>0; i++)
	            prob-= probs.get(i);
	        
	        return events.get(i-1);
	    }
	}

}
