/*****************************************************************************
 *                        Shapeways, Inc Copyright (c) 2012
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/

package abfab3d.io.input;


import javax.vecmath.Vector3d;

import abfab3d.util.Vec;
import abfab3d.util.AttributedTriangleCollector;
import abfab3d.util.AttributedTriangleProducer;
import abfab3d.util.Bounds;
import abfab3d.util.DataSource;
import abfab3d.util.PointSetCoordArrays;

import abfab3d.grid.AttributeGrid;
import abfab3d.grid.ArrayAttributeGridInt;
import abfab3d.grid.GridMask;

import abfab3d.grid.op.ClosestPointIndexer;
import abfab3d.grid.op.ClosestPointIndexerMT;

import abfab3d.grid.util.GridUtil;

import abfab3d.geom.AttributedTriangleMeshSurfaceBuilder;
import abfab3d.grid.op.PointSetShellBuilder;


import static abfab3d.util.Units.MM;
import static abfab3d.util.Output.printf;
import static abfab3d.util.Output.time;
import static abfab3d.util.MathUtil.step10;


/**
   creates antialiased signed distance grid to attributed triangle mesh 
   each triangle vertex shall have 3 coordinates and few double attributes
   output grid is contains both distance and attributes information stored in its GridDataDesc 
   triangles are sent via TraingleCollector2 interface 
   
   - each triangle is rasterized on a regular grid and we obtain AttributedPointSet of points on the mesh
   - thin shell is build around AttributedPointSet with exact distances calculated at grid points inside of the shell 
   - shell points are sweeped to the whole grid using ClosestPointIndexer and each grid point is assigned ndex of closest point
   - z-buffer rasterizer calculates interior of the mesh
   - voxels distances are calculated as distances to closes point. Interior points are assigned negative distance value
   - voxels attributes are calculated as attributes of the closest point 

   @author Vladimir Bulatov

 */
public class AttributedDistanceRasterizer implements AttributedTriangleCollector {

    static final boolean DEBUG = true;
    static final boolean DEBUG_TIMING = true;


    // this is used purely for precision of distance calculations on distance grid    
    //long m_subvoxelResolution=100;
    // size of grid 
    int gridX,gridY,gridZ;

    // z-buffer rasterizer to get mesh interior 
    MeshRasterizer m_rasterizer;     
    // triangles rasterizer 
    AttributedTriangleMeshSurfaceBuilder m_surfaceBuilder;
    // builder of shell around rasterized points 
    PointSetShellBuilder m_shellBuilder;

    Bounds m_bounds;
    AttributeGrid m_indexGrid;
    // max value to calculate exterior distances   
    double m_maxInDistance = 1.*MM;
    // max value to calculate interior distance 
    double m_maxOutDistance = 1*MM;
    // flag to use distance calculation with limited range. Setting it true increases perpormance especiualy if needed 
    // distanace range is small
    boolean m_useDistanceRange = true;
    double m_maxDistanceVoxels; // max distance to calculate in case of using distance range 
    protected int m_threadCount = 1;

    // size of surface voxels relative to size fo grid voxles 
    protected double m_surfaceVoxelSize = 1.;
    // total dimnenson of data (3 coord + attributes)
    protected int m_dataDimension = 3;

    int m_triCount = 0;

    // half thickness of initial shell around the mesh (in voxels )
    double m_shellHalfThickness = 1.0;

    public AttributedDistanceRasterizer(Bounds bounds, int gridX, int gridY, int gridZ){
        
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.m_bounds = bounds.clone();
        
    }
    
    
    /**
       set relative size of voxel used for mesh surface rasterization
       Default value is 1 
       smalller value will cause generation of more points on surface and will increase distance precision
     */
    public void setSurfaceVoxelSize(double voxelSize){

        m_surfaceVoxelSize = voxelSize;

    }

    /**
       set maximal interior distance to calculate 
     */
    public void setMaxInDistance(double value){
        m_maxInDistance = value;
    }

    /**
       set maximal exterior distance to calculate 
     */
    public void setMaxOutDistance(double value){

        m_maxOutDistance = value;

    }

    public void setUseDistanceRange(boolean value){

        m_useDistanceRange = value;

    }

    /**
       set thread count for MT parts of algorithm 
     */
    public void setThreadCount(int threadCount){

        m_threadCount = threadCount;

    }

    /**
       set thickness of initial shel whiuch is build around rastrerised surface
     */
    public void setShellHalfThickness(double value){  

        m_shellHalfThickness = value;

    }

    /**
       sets dimension of data (3 coord + attributes) 
     */
    public void setDataDimension(int dataDimension){
        m_dataDimension = dataDimension;
    }

    
    protected int init(){

        m_rasterizer = new MeshRasterizer(m_bounds, gridX, gridY, gridZ);
        m_rasterizer.setInteriorValue(1);

        m_indexGrid = createIndexGrid();
        
        Bounds surfaceBounds = m_bounds.clone();
        surfaceBounds.setVoxelSize(m_bounds.getVoxelSize()*m_surfaceVoxelSize);
        m_surfaceBuilder = new AttributedTriangleMeshSurfaceBuilder(surfaceBounds);        
        m_surfaceBuilder.setDataDimension(m_dataDimension);
        m_surfaceBuilder.initialize();

        m_shellBuilder = new PointSetShellBuilder();
        
        m_shellBuilder.setShellHalfThickness(m_shellHalfThickness);

        if(m_useDistanceRange) m_maxDistanceVoxels = Math.max(m_maxInDistance, m_maxOutDistance)/m_bounds.getVoxelSize();
        else m_maxDistanceVoxels = 0.;

        return DataSource.RESULT_OK;
    }


    protected AttributeGrid createIndexGrid(){
        
        double vs = m_bounds.getVoxelSize();
        if(DEBUG)printf("index grid bounds: %s  voxelSize: %7.5f\n", m_bounds, vs);
        return new ArrayAttributeGridInt(m_bounds, vs, vs);

    }

    Vector3d  // work vectors
        w0 = new Vector3d(), 
        w1 = new Vector3d(), 
        w2 = new Vector3d();

    /**
       interface of AttributedTriangleCollector
     */
    public boolean addAttTri(Vec v0,Vec v1,Vec v2){
        v0.get(w0);
        v1.get(w1);
        v2.get(w2);
        m_rasterizer.addTri(w0, w1, w2);
        m_surfaceBuilder.addAttTri(v0, v1, v2);
        m_triCount++;
        return true;
    }


    /**
       Calculates distances on distanceGrid from given mesh 
       @param triProducer - the mesh 
       @param distanceGrid grid to contain distances to the mesh 
     */
    public void getDistances(AttributedTriangleProducer triProducer, DataSource attributeColorizer, AttributeGrid distanceGrid){
        
        if(DEBUG)printf("DistanceRasterizer2.getDistances(grid)\n");
        long t0 = time();

        init();
        triProducer.getAttTriangles(this);
        if(DEBUG_TIMING)printf("triProducer.getTriangles(this) time: %d ms\n", (time() - t0));

        int pcount = m_surfaceBuilder.getPointCount();
        if(DEBUG)printf("pcount: %d\n", pcount);

        double pnt[][] = new double[m_dataDimension][pcount];
        t0 = time();

        m_surfaceBuilder.getPoints(pnt);

        m_shellBuilder.setPoints(new PointSetCoordArrays(pnt[0], pnt[1], pnt[2]));
        m_shellBuilder.setShellHalfThickness(m_shellHalfThickness);

        t0 = time();
        m_shellBuilder.execute(m_indexGrid);

        if(DEBUG_TIMING)printf("m_shellBuilder.execute() %d ms\n", (time() - t0));

        AttributeGrid interiorGrid = new GridMask(gridX,gridY,gridZ);
        
        t0 = time();
        m_rasterizer.getRaster(interiorGrid);

        if(DEBUG_TIMING)printf("m_rasterizer.getRaster(interiorGrid) time: %d ms\n", (time() - t0));


        t0 = time();

        // distribute indices on the whole indexGrid        
        
        ClosestPointIndexer.getPointsInGridUnits(m_indexGrid, pnt[0], pnt[1], pnt[2]);
        
        if(m_threadCount <= 1) {
            ClosestPointIndexer.PI3_bounded(pnt[0], pnt[1], pnt[2], m_maxDistanceVoxels, m_indexGrid);
            if(DEBUG_TIMING)printf("ClosestPointIndexer.PI3_sorted time: %d ms\n", (time() - t0));
        } else {
            ClosestPointIndexerMT.PI3_MT(pnt[0], pnt[1], pnt[2], m_maxDistanceVoxels, m_indexGrid, m_threadCount);
            if(DEBUG_TIMING)printf("ClosestPointIndexerMT.PI3_MT time: %d ms\n", (time() - t0));
        }
        
        t0 = time();
        // transform points into world units
        ClosestPointIndexer.getPointsInWorldUnits(m_indexGrid, pnt[0], pnt[1], pnt[2]);
        if(DEBUG_TIMING)printf("ClosestPointIndexer.getPointsInWorldUnits(): %d ms\n", (time() - t0));
        
        
        t0 = time();
        if(m_threadCount <= 1) {
            ClosestPointIndexer.makeDistanceGrid(m_indexGrid, pnt[0], pnt[1], pnt[2], interiorGrid, distanceGrid, m_maxInDistance, m_maxOutDistance);
            if(DEBUG_TIMING)printf("ClosestPointIndexer.makeDistanceGrid()ime: %d ms\n", (time() - t0));
        } else {
            ClosestPointIndexerMT.makeDistanceGrid_MT(m_indexGrid, pnt[0], pnt[1], pnt[2], interiorGrid, distanceGrid, m_maxInDistance, m_maxOutDistance, m_threadCount);
            if(DEBUG_TIMING)printf("ClosestPointIndexerMT.makeDistanceGrid_MT()ime: %d ms\n", (time() - t0));
        }
       
    } // getDistances()

}