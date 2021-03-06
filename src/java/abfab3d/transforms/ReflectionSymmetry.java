/*****************************************************************************
 *                        Shapeways, Inc Copyright (c) 2011
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/

package abfab3d.transforms;

import javax.vecmath.Vector3d;

import abfab3d.core.DataSource;
import abfab3d.core.ResultCodes;
import abfab3d.param.SNodeParameter;
import abfab3d.param.DoubleParameter;
import abfab3d.param.BooleanParameter;
import abfab3d.param.IntParameter;
import abfab3d.param.Parameter;
import abfab3d.param.Parameterizable;

import abfab3d.core.Vec;
import abfab3d.core.Initializable;
import abfab3d.util.ReflectionGroup;
import abfab3d.core.VecTransform;
import abfab3d.core.Units;
import abfab3d.util.SymmetryGenerator;

import static abfab3d.core.Output.printf;
import static abfab3d.util.Symmetry.toFundamentalDomain;



/**
   <p>
   Makes transformations for reflection symmetry group generated by reflections in planes and invesions in spheres.
   </p>
   <p>
   The fundamental domain of the group is represented as intersection of half spaces and spheres. 
   </p>
   <p>
   The generators of the group are reflections and inversions in the sides of the fundamental domain 
   </p>
   <p>
   It can be used to generate <a href="http://en.wikipedia.org/wiki/Reflection_group"> reflection groups </a>
   in various geometries. See also artice on <a href = "http://en.wikipedia.org/wiki/Inversive_geometry">invesive geomety</a>.
   </p>
   
   @author Vladimir Bulatov
*/
public class ReflectionSymmetry  extends BaseTransform implements VecTransform, Initializable  {
    
    static final boolean DEBUG = false;
    static int debugCount = 1000;    
    
    private ReflectionGroup m_group;
    //double riemannSphereRadius;
    //int m_maxCount = 100;
   
    //ObjectParameter  mp_splanes = new ObjectParameter("splanes","array of splanes",defaultSplanes);

    SNodeParameter mp_generator = new SNodeParameter("generator", "symmetry generator", new ReflectionSymmetries.Plane(), ReflectionSymmetries.getFactory());
    IntParameter  mp_iterations = new IntParameter("iterations","max iterations to reflect into fundamental domain",100);
    DoubleParameter  mp_riemannSphereRadius = new DoubleParameter("riemannSphereRadius","Riemann Sphere Radius",0.);
    BooleanParameter mp_g1 = new BooleanParameter("g1", true);
    BooleanParameter mp_g2 = new BooleanParameter("g2", true);
    BooleanParameter mp_g3 = new BooleanParameter("g3", true);
    BooleanParameter mp_g4 = new BooleanParameter("g4", true);
    BooleanParameter mp_g5 = new BooleanParameter("g5", true);
    BooleanParameter mp_g6 = new BooleanParameter("g6", true);
    BooleanParameter mp_g7 = new BooleanParameter("g7", true);
    BooleanParameter mp_g8 = new BooleanParameter("g8", true);


    Parameter m_aparam[] = new Parameter[]{
        //mp_splanes,
        mp_generator,
        mp_iterations,
        mp_riemannSphereRadius,
        mp_g1,
        mp_g2,
        mp_g3,
        mp_g4,
        mp_g5,
        mp_g6,
        mp_g7,
        mp_g8,
    };
    
    BooleanParameter m_gens[] = {
        mp_g1,
        mp_g2,
        mp_g3,
        mp_g4,
        mp_g5,
        mp_g6,
        mp_g7,
        mp_g8,
    };

    /**
      Reflection symmetry with empty fundamental domain
     */
    public ReflectionSymmetry(){            
        super.addParams(m_aparam);
    }

    /**
       creates reflection symmetyr with given generator
     */
    public ReflectionSymmetry(SymmetryGenerator generator){            
        super.addParams(m_aparam);
        
        mp_generator.setValue(generator);
    }
    
    /**
       Reflection symmetry with specified fundamental domain
     */
    public ReflectionSymmetry(ReflectionGroup.SPlane fundamentalDomain[]){
        super.addParams(m_aparam);
        mp_generator.setValue(new ReflectionSymmetries.General(fundamentalDomain));
    }

    /**
       set max count of reflection to be used in group generation. Default value is 100.
     */
    public void setIterations(int count){
        mp_iterations.setValue(count);
    }
    
    /**
       @noRefGuide 
     */
    public void setRiemannSphereRadius(double value){
        mp_riemannSphereRadius.setValue(value);
    }
    

    public ReflectionGroup getReflectionGroup(){
        
        if(m_group == null) 
            initialize();
        return m_group;
    }

    /**
       @noRefGuide
     */
    public int initialize(){
        
        SymmetryGenerator gen = (SymmetryGenerator)mp_generator.getValue();
        ReflectionGroup.SPlane[] splanes = gen.getFundamentalDomain();
        ReflectionGroup.SPlane[] activeSplanes = splanes;        
        /*
          // TODO need to remove liitation on generators number 
        int count = 0;
        for(int i = 0; i < splanes.length; i++){            
            if(m_gens[i].getValue()) 
                count++;
        }
        ReflectionGroup.SPlane[] activeSplanes = new ReflectionGroup.SPlane[count];
        count = 0;
        for(int i = 0; i < splanes.length; i++){
            if(m_gens[i].getValue()){
                activeSplanes[count] = splanes[i];
                count++;
            }
        }        
        */
        m_group = new ReflectionGroup(activeSplanes);
                
        m_group.setRiemannSphereRadius(mp_riemannSphereRadius.getValue());
        m_group.setMaxIterations(mp_iterations.getValue());
        return ResultCodes.RESULT_OK;
    }
    
    /**
       @noRefGuide
     */
    public int transform(Vec in, Vec out) {
        // direct transform is identity transform 
        out.set(in);
        // TODO we may use one specific element from the group
        return ResultCodes.RESULT_OK;
        
    }


    /**
       @noRefGuide
     */
    public int inverse_transform(Vec in, Vec out) {

        out.set(in);

        if(DEBUG && debugCount-- > 0)
            printf("vs before: %5.3f\n", in.getScaledVoxelSize()/Units.MM);

        int res = m_group.toFundamentalDomain(out);

        if(DEBUG && debugCount-- > 0)
            printf("vs after: %5.3f\n", out.getScaledVoxelSize()/Units.MM);

        return res;
    }

    /**
       makes plane defined via external normal and distance from origin. 

       @param normal external unit normal to the plane. The extenal normal points outiside of the half space used 
       for fundamental domain definition. 
       @param distance Distance from plane to origin. Distance may be positive or negative. 
       
     */
    public static ReflectionGroup.SPlane getPlane(Vector3d normal, double distance){
        return new ReflectionGroup.Plane(normal, distance);
    }

    /**
     * makes is defined via 3 points on the plane oriented counter clock wise
     *
     * @param pnt0 point in the plane
     * @param pnt1 point in the plane
     * @param pnt2 point in the plane
     */
    public static ReflectionGroup.SPlane getPlane(Vector3d pnt0,Vector3d pnt1,Vector3d pnt2){
        return new ReflectionGroup.Plane(pnt0, pnt1, pnt2);
    }


    /**
     * makes plane defined via external normal and a point on the plane
     *
     * @param normal The normal to the plane
     * @param pointOnPlane the point on the plane
     */
    public static ReflectionGroup.SPlane getPlane(Vector3d normal,Vector3d pointOnPlane){
        return new ReflectionGroup.Plane(normal, pointOnPlane);
    }

    /**
       makes sphere defined via center and radius
       @param center  sphere center
       @param radius 
       <ul>
       <li> if radius is positive - the interior of sphere is used </li>
       <li> if radius is negative - the exterior of sphere is used   </li>
       </ul>
       
     */
    public static ReflectionGroup.SPlane getSphere(Vector3d center, double radius){
        return new ReflectionGroup.Sphere(center, radius);
    }

    public static DataSource getDataSource(ReflectionGroup.SPlane splane){
        if(splane instanceof ReflectionGroup.Plane){
            ReflectionGroup.Plane plane = (ReflectionGroup.Plane)splane;
            return (DataSource)createParameterizable("abfab3d.datasources.Plane", 
                                                 new Object[]{
                                                     "normal",plane.getNormal(),
                                                     "dist",plane.getDistance()}
                                                 );
        } else if(splane instanceof ReflectionGroup.Sphere ){

            ReflectionGroup.Sphere sphere = (ReflectionGroup.Sphere)splane;
            return (DataSource)createParameterizable("abfab3d.datasources.Sphere", 
                                         new Object[]{
                                             "center",sphere.getCenter(),
                                             "radius",sphere.getRadius()}
                                         );            
        } else {
            
            return null;
        }
    }

    public static Parameterizable createParameterizable(String className, Object[] params){

        Object inst = null;
        try {
            Class cl = Class.forName(className);
            inst = cl.newInstance();
        } catch(Exception e){
            e.printStackTrace();
            return null;
        }

        if(!(inst instanceof Parameterizable))
            return null;
        Parameterizable prm = (Parameterizable)inst;
        
        for(int i = 0; i < params.length/2; i++){
            prm.set((String)params[2*i], params[2*i+1]);
        }
        return prm;
    }

    /*
    public static DataSource getIntersection(ReflectionGroup.SPlane splanes[]){

        abfab3d.datasources.Intersection inter = new abfab3d.datasources.Intersection();
        for(int i = 0; i < splanes.length; i++){
            inter.add(getDataSource(splanes[i]));
        }
        return inter;
    }
    */
} // class ReflectionSymmetry 

