package process;

import java.io.File;
import java.io.IOException;

import org.janelia.utility.parse.ParseUtils;

import bigwarp.landmarks.LandmarkTableModel;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import transforms.AffineHelper;

public class FlipVariancePointPairsJFRC2013 extends EstimateLeftRightVariance_JFRC2013
{
	
	LandmarkTableModel ltm;

	public <T extends RealType<T>> void buildPointPairs(
			long[] factor,
			Img<T> mask, 
			InvertibleRealTransform totalXfm,
			InvertibleRealTransform totalXfmFlip,
			InvertibleRealTransform templateFlip )
	{
		
		ltm = new LandmarkTableModel( 3 );
		
		double[] x = new double[ 3 ];
		double[] xFlip = new double[ 3 ];
		double[] xSrc = new double[ 3 ];
		double[] xSrcFlip = new double[ 3 ];

		
		
//		Cursor< T > cursor = mask.cursor();
		Cursor< T > cursor = 
				Views.flatIterable( Views.subsample( mask, factor )).cursor();

		int i = 0;

		while( cursor.hasNext() )
		{
			cursor.fwd();
			
			if( cursor.get().getRealDouble() <= 0 )
			{
				continue;
			}
			
			cursor.localize( x );
			
			for( int d = 0; d < x.length; d++ )
				x[ d ] *= factor[ d ];
			
			templateFlip.applyInverse( xFlip, x );
			
			totalXfm.applyInverse( xSrc, x);
			totalXfmFlip.applyInverse( xSrcFlip, xFlip );
			
			ltm.add( xSrc, false );
			ltm.setPoint( i, true, xSrcFlip, null );
			ltm.setValueAt( String.format( "( %f, %f, %f )", x[0], x[1], x[2] ), 
					i, 0 );
			
			i++;
		}
	}
	

	public static void main( String[] args ) throws IOException, FormatException 
	{
		String outPointList = args[ 0 ];
		String templateF = args[ 1 ];
		String templateFlipF = args[ 2 ];
		String templateFlipAdjustF = args[ 3 ];

		String affineF = args[ 4 ];
		String warpF = args[ 5 ];
		String unflipToCanF = args[ 6 ];

		String affineFlipF = args[ 7 ];
		String warpFlipF = args[ 8 ];
		String fliptoCanF = args[ 9 ];
		
		long[] factor = ParseUtils.parseLongArray( args[ 10 ] );

		System.out.println("template: " + templateF );
		Img< FloatType > baseImg = ImageJFunctions.convertFloat( IJ.openImage( templateF ));
		
		String templateSymDefF = "/nrs/saalfeld/john/projects/flyChemStainAtlas/flipComparisons/JFRC2013/symmetry/JFRC2013_222-flip-toOrigWarp.nii";
		
		//FinalInterval destInterval = new FinalInterval( 765, 766, 303 );

		// DO THE WORK
		FlipVariancePointPairsJFRC2013 alg = new FlipVariancePointPairsJFRC2013();
		
		System.out.println("build unflipped");
		System.out.println("preXfm : unflipToCanF:  " + unflipToCanF);
		InvertibleRealTransformSequence totalXfm = alg.buildTransform( unflipToCanF, affineF, warpF );
		
		System.out.println("build flipped");
		InvertibleRealTransformSequence totalXfmFlip = alg.buildTransform( fliptoCanF, affineFlipF, warpFlipF ); 

		System.out.println( "totalXfm    : " + totalXfm );
		System.out.println( "totalXfmFlip: " + totalXfmFlip );

		AffineTransform3D templateFlip = AffineHelper.to3D( AffineImglib2IO.readXfm( 3, new File( templateFlipF ) ));
		AffineTransform3D templateAdjust = ANTSLoadAffine.loadAffine( templateFlipAdjustF );
		templateFlip.preConcatenate( templateAdjust.inverse() );
		
		ImagePlus ip = NiftiIo.readNifti( new File( templateSymDefF ) );
		Img< FloatType > defLowImg = ImageJFunctions.convertFloat( ip );
		ANTSDeformationField templateDf = new ANTSDeformationField( defLowImg, new double[]{ 1, 1, 1 }, ip.getCalibration().getUnit() );
		
		InvertibleRealTransformSequence symmetrizingTransform = new InvertibleRealTransformSequence();
		symmetrizingTransform.add( templateFlip );
		symmetrizingTransform.add( templateDf );

		
		
		System.out.println( "computing" );
		alg.buildPointPairs( factor, baseImg, totalXfm, totalXfmFlip, symmetrizingTransform );
		
		System.out.println( "saving to: " + outPointList );
		alg.ltm.save( new File( outPointList ) );
	}

}
