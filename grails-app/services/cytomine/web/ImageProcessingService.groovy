package cytomine.web

import ij.ImagePlus
import com.vividsolutions.jts.geom.Coordinate
import ij.plugin.filter.PlugInFilter
import ij.gui.PolygonRoi
import ij.gui.Roi
import ij.gui.Wand
import ij.process.ImageProcessor

class ImageProcessingService {

    private static def BLACK = 0
    private static def WHITE = 255


    static transactional = false

    public def isInROI(ImagePlus ip, x, y) {
        return (x >= 0 && x < ip.getWidth() && y >= 0 && y < ip.getHeight())
    }

    public String getWKTPolygon(Coordinate[] coordinates) {
        String[] coordinatesSTR = new String[coordinates.size()]
        for (int i = 0; i < coordinates.size(); i++) {
            coordinatesSTR[i] = coordinates[i].x + " " + coordinates[i].y
        }
        String polygonSTR = "POLYGON(("
        polygonSTR += coordinatesSTR.join(",")
        polygonSTR += "))"
        return polygonSTR
    }

    public Collection<Coordinate[]> getConnectedComponents(ImagePlus ori, ImagePlus img, int minNumberOfPixels) {
        PlugInFilter filler = new ij.plugin.filter.Binary()
        filler.setup("fill", img)
        filler.run(img.getProcessor())
        filler = new ij.plugin.filter.Binary()
        filler.setup("fill", ori)
        filler.run(ori.getProcessor())
        int k = 0
        Collection<Coordinate[]> components = new ArrayList<Coordinate[]>()
        Collection<Coordinate[]> polygons = new ArrayList<Coordinate[]>()
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int pixelValue = img.getProcessor().getPixel((int) x, (int) y)
                if (pixelValue == BLACK) {
                    Coordinate[] coordinates = computeCoordinates(ori, img, x, y)
                    if (coordinates.size() > minNumberOfPixels) {
                        Coordinate[] c = new Coordinate[1]
                        //call magic wand on pixel
                        c[0] = new Coordinate((int)x,(int)y)
                        components.add(c)
                    }
                }
            }
        }
        components.each { component ->
            Coordinate firstCoordinate = component[0]
            Coordinate[] coordinates = doWand(ori, (int) firstCoordinate.x, (int) firstCoordinate.y, 4, null)
            polygons.add(coordinates)
        }
        return polygons
    }

    public static Coordinate[] doWand(ImagePlus img, int x, int y, double tolerance, String mode) {
        println "DoWand (X,Y) : (" + x + "," + y + ")"
        Collection<Coordinate> coordinates = new ArrayList<Coordinate>()
        long start = new Date().getTime()
        ImageProcessor ip = img.getProcessor()
        if ((img.getType()==ImagePlus.GRAY32) && Double.isNaN(ip.getPixelValue(x,y)))
            return 0
        int imode = Wand.LEGACY_MODE;
        if (mode!=null) {
            if (mode.startsWith("4"))
                imode = Wand.FOUR_CONNECTED
            else if (mode.startsWith("8"))
                imode = Wand.EIGHT_CONNECTED
        }
        Wand w = new Wand(ip)
        double t1 = ip.getMinThreshold()
        if (t1==ImageProcessor.NO_THRESHOLD || (ip.getLutUpdateMode()==ImageProcessor.NO_LUT_UPDATE&& tolerance>0.0))
            w.autoOutline(x, y, tolerance, imode)
        else
            w.autoOutline(x, y, t1, ip.getMaxThreshold(), imode)

        if (w.npoints>0) {
            int type = Wand.allPoints()?Roi.FREEROI:Roi.TRACED_ROI
            Roi roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, type)

            for(int i=0;i<w.xpoints.length;i++) {

                if(w.xpoints[i]==0 && w.ypoints[i]==0) break

                Coordinate coordinate = new Coordinate(
                        w.xpoints[i] + roi.getBounds().getX(),
                        w.ypoints[i] + roi.getBounds().getY())
                coordinates.add(coordinate)
            }
            coordinates.add(new Coordinate(
                    w.xpoints[0] + roi.getBounds().getX(),
                    w.ypoints[0] + roi.getBounds().getY()))


            long stop = new Date().getTime()
            long time = stop - start
            System.out.println("time="+time)
            img.killRoi()
            img.setRoi(roi)
        }
        Coordinate[] coordinatesArray = new Coordinate[coordinates.size()]
        coordinates.toArray(coordinatesArray)
        return coordinatesArray
    }

    public Coordinate[] computeCoordinates(ImagePlus ori, ImagePlus img, int x, int y) {
        int[] firstPixel = img.getPixel(x, y)
        if (firstPixel[0] == WHITE) { //pixel is white, nothing to do
            println "PIXEL IS WHITE !!!!"
            return null
        }
        Stack<Coordinate> toVisit = new Stack<Coordinate>()
        Stack<Coordinate> interestingPoints = new Stack<Coordinate>()
        //List<Coordinate> visited = new ArrayList<Coordinate>()
        toVisit.push(new Coordinate(x, y))
        img.getProcessor().putPixel(x, y, 255)
        assert (img.getProcessor().getPixel((int) x, (int) y) == WHITE)
        while (!toVisit.empty()) {
            Coordinate point = toVisit.pop()
            //visited.push(new Coordinate(point.x, point.y)) //compute the real coordinate, not relative to the crop

            int posX
            int posY

            int[] xShifts = [-1, 0, 1,
                    -1, 1,
                    -1, 0, 1]

            int[] yShifts = [-1, -1, -1,
                    0, 0,
                    1, 1, 1]

            assert (xShifts.size() == yShifts.size())
            boolean oneNeighborIsWhite = false
            for (int i = 0; i < xShifts.size(); i++) {
                posX = (int) point.x + xShifts[i]
                posY = (int) point.y + yShifts[i]
                if (isInROI(ori, posX, posY) && ori.getProcessor().getPixel(posX, posY) == WHITE) {
                    oneNeighborIsWhite = true
                }
                if (isInROI(img, posX, posY) && img.getProcessor().getPixel(posX, posY) != WHITE) {
                    img.getProcessor().putPixel(posX, posY, WHITE)
                    toVisit.push(new Coordinate(posX, posY))
                }
            }
            if (oneNeighborIsWhite) interestingPoints.push(new Coordinate(posX, posY))
        }

        //return to coordinates array
        Coordinate[] coordinates = new Coordinate[interestingPoints.size()]
        interestingPoints.toArray(coordinates)
        coordinates
    }
}
