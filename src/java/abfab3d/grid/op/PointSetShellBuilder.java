/** 
 *                        Shapeways, Inc Copyright (c) 2015
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/

package abfab3d.grid.op;

import javax.vecmath.Vector3d;

import abfab3d.core.AttributeGrid;
import abfab3d.grid.AttributeOperation;
import abfab3d.grid.ArrayAttributeGridByte;

import abfab3d.core.Bounds;
import abfab3d.util.PointSet;
import abfab3d.util.PointSetArray;

import static java.lang.Math.sqrt;
import static java.lang.Math.max;
import static java.lang.Math.abs;
import static java.lang.Math.min;

import static abfab3d.core.Output.time;
import static abfab3d.core.Output.printf;
import static abfab3d.core.Units.MM;
import static abfab3d.core.MathUtil.clamp;


/**
   build narrow shell around PointSet

   indices of points closest to given grid point are stored in the indexGrid


   @author Vladimir Bulatov
 */
public class PointSetShellBuilder implements AttributeOperation {

    static final boolean DEBUG = false;
    static final double TOL = 1.e-2;
    static final double HALF = 0.5; // half voxel offset to the center of voxel
    
    static final int DEFAULT_SVR = 10;
    double m_layerThickness = 1;  // 2.0, 2.25, 2.45*, 2.84, 3.0 3.17 3.33*, 3.46, 3.62, 3.74*   * - good values 
    int m_neighbors[]; // offsets to neighbors 
    long m_subvoxelResolution = DEFAULT_SVR;
    double m_voxelSize;
    Bounds m_bounds;

    // grid to world conversion params 
    double m_xmin, m_ymin, m_zmin, m_scale;
    // grud dimension 
    int m_nx, m_ny, m_nz;
    
    PointSet m_points;
        // grid to store indexes of closest point 
    AttributeGrid m_indexGrid; 
    // grid to store current shortest distances 
    AttributeGrid m_distanceGrid; 

    
    public PointSetShellBuilder(){

    }

    /**
       @param points set of points. Coordinates of points should be in grid units. 
       
     */
    public PointSetShellBuilder(PointSet points){

        m_points = points;

    }

    /*
       @param points set of points. Coordinates of points should be in grid units. 

     */
    public void setPoints(PointSet points){
        m_points = points;
    }
    
    public void setShellHalfThickness(double shellHalfThickness){

        m_layerThickness = shellHalfThickness;

    }
   
    public AttributeGrid execute(AttributeGrid indexGrid) {
        
        m_indexGrid = indexGrid;

        init();
        calculateShell();

        return m_indexGrid;

    }

    public AttributeGrid getDistanceGrid(){
        return m_distanceGrid;
    }

    protected void init(){

        m_bounds = m_indexGrid.getGridBounds();
        m_voxelSize = m_indexGrid.getVoxelSize();
        m_nx = m_indexGrid.getWidth();
        m_ny = m_indexGrid.getHeight();
        m_nz = m_indexGrid.getDepth();

        m_distanceGrid = new ArrayAttributeGridByte(m_bounds, m_voxelSize,m_voxelSize);

        m_xmin = m_bounds.xmin;
        m_ymin = m_bounds.ymin;
        m_zmin = m_bounds.zmin;
        m_scale = 1/m_voxelSize;

        m_neighbors = Neighborhood.makeBall(m_layerThickness);

        if(DEBUG) {
            printf("PointSetShellBilder: grid: (%d x %d x %d)\n", m_nx, m_ny, m_nz);
            printf("PointSetShellBilder: layerThickness: %5.2f\n",m_layerThickness);
            printf("PointSetShellBilder: neighboursCount: %d\n",m_neighbors.length/3);
        }
    }

    //
    // something wrong does not work 
    //
    public static PointSet makeSortedPoints(PointSet pnts, int binCount, double binMin, double binSize){
        
        long t0 = time();
        Vector3d p = new Vector3d();

        int pcount = pnts.size();        

        int binCounts[] = new int[binCount];
        int maxBin = binCount-1;
        double y0 = binMin;
        double scale = 1/binSize;
        // first point is not used 
        for(int i = 1; i < pcount;i++){
            pnts.getPoint(i, p);
            int bin = clamp((int)((p.y-y0)*scale), 0, maxBin);
            binCounts[bin]++;
        }

        //for(int bin = 0; bin < binCount;bin++){
            //printf("binCount[%3d]: %6d\n", bin, binCounts[bin]);
        //}

        //
        // array of bins offsets 
        //
        int binOffset[] = new int[binCount];
        int offset = 0;
        for(int bin = 0; bin < binCount;bin++){
            binOffset[bin] = offset;
            offset += binCounts[bin];
            binCounts[bin] = 0;
        }
        //for(int bin = 0; bin < binCount;bin++){
            //printf("offset[%3d]: %6d cnt: %d\n", bin, binOffset[bin], binCounts[bin]);
        //}

        double coord[] = new double[pcount*3];

        pnts.getPoint(0, p);
        coord[0] = p.x;
        coord[1] = p.y;
        coord[2] = p.z;

        // first point is not used 
        for(int i = 1; i < pcount;i++){

            pnts.getPoint(i, p);
            int bin = clamp((int)((p.y-y0)*scale), 0, maxBin);

            int cindex = 3*(binOffset[bin] + binCounts[bin] + 1);
            binCounts[bin]++;
            
            coord[cindex  ] = p.x;
            coord[cindex+1] = p.y;
            coord[cindex+2] = p.z;
            
        }
        
        printf("makeSortedSet() count: %d time: %d ms\n", pcount, time() - t0);
        return new PointSetArray(coord);
    }

    protected void calculateShell(){

        int npnt = m_points.size();
        Vector3d pnt = new Vector3d();
        
        final double y0 = m_ymin, x0 = m_xmin, z0 = m_zmin,scale = m_scale;
        
        for(int i = 1; i < npnt; i++){// start from 1. Index 0 means undefined
            
            m_points.getPoint(i, pnt);
            processNeighborhood(i, (pnt.x-x0)*scale, (pnt.y-y0)*scale, (pnt.z-z0)*scale);
        }
    }

    //
    // point cordinates are in voxels
    //
    final void processNeighborhood(int pointIndex, double x, double y, double z){
        
        int 
            x0 = (int)x,
            y0 = (int)y,
            z0 = (int)z;

        //printf("point: %3d, (%6.2f,%6.2f,%6.2f): (%2d %2d %2d)\n", pointIndex, x,y,z, x0, y0, z0);
        // scan over neighborhood of the voxel 
        int ncount = m_neighbors.length;
        
        for(int i = 0; i < ncount; i+=3){
            int 
                vx = x0 + m_neighbors[i],
                vy = y0 + m_neighbors[i+1],
                vz = z0 + m_neighbors[i+2];  
            
            //  printf("(%2d %2d %2d )\n", vx, vy, vz);
            if( vx >= 0 && vy >= 0 && vz >= 0 && 
                vx < m_nx && vy < m_ny && vz < m_nz){
                // center of voxels have coordinates shifted by HALF
                double 
                    dx = (vx - x)+HALF,
                    dy = (vy - y)+HALF,
                    dz = (vz - z)+HALF;

                //printf("dx: (%6.2f,%6.2f,%6.2f)\n", dx, dy, dz);
                double d2 = dx*dx + dy*dy + dz*dz;
                // distances are flipped ! 
                // this is to make initial grid 0 
                // distance at points are m_layerThickness
                long newdist = iround((m_layerThickness - sqrt(d2))*m_subvoxelResolution)+1;                
                //  printf("   newdist: %d\n",newdist);
                if(newdist >= 0 ) {
                    long olddist = (m_distanceGrid.getAttribute(vx, vy, vz));
                    if(newdist > olddist){
                        // better point found
                        m_distanceGrid.setAttribute(vx, vy, vz, newdist);
                        m_indexGrid.setAttribute(vx, vy, vz, pointIndex);
                    }
                }
            }
        }                    
    } // add neighborhood     

    static void printPointSet(PointSet points){
        
        Vector3d pnt = new Vector3d();
        
        for(int i = 0; i < points.size(); i++){
            points.getPoint(i, pnt);
            printf("%7.3f %7.3f %7.3f mm\n", pnt.x/MM,pnt.y/MM,pnt.z/MM);
        }
    }

    static final int iround(double x) {
        return (int)(x + 0.5);
    }

}