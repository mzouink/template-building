package net.imglib2.posField;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import process.RenderTransformed;


/**
 * Currently only intended to work for 3D
 *
 */
public class Position2DisplacementField
{
	public static final String X_COMPONENT = "--x";
	public static final String Y_COMPONENT = "--y";
	public static final String Z_COMPONENT = "--z";

	public static void main( String[] args ) throws ImgLibException
	{
		
		int argIdx = 0;
		
		int dim_component = -1;
		String suffix = "";
		if( args[ argIdx ].equals( X_COMPONENT ))
		{
			dim_component = 0;
			suffix="-x";
			argIdx++;
		}
		else if( args[ argIdx ].equals( Y_COMPONENT ))
		{
			dim_component = 1;
			suffix="-y";
		}
		else if( args[ argIdx ].equals( Z_COMPONENT ))
		{
			dim_component = 2;
			suffix="-z";
		}
		
		String outArg = args[ argIdx++ ];

		RandomAccessibleInterval<FloatType> posField = null; 
		if ( args.length == argIdx + 1 )
		{
			System.out.println( "reading single image");
			posField = read( args[ argIdx++ ] );
		}
		else
		{
			System.out.println( "reading image stack");
			ArrayList<RandomAccessibleInterval<FloatType>> list = 
					new ArrayList<RandomAccessibleInterval<FloatType>>( args.length - 1 );
			for( int i = argIdx; i < args.length; i++ )
			{
				list.add( read( args[ i ]));
			}
			posField = Views.stack( list );
		}

//		long[] tmp = Intervals.dimensionsAsLongArray( posField );
//		long[] dim = new long[ tmp.length ];
//		dim[ 0 ] = tmp[ 0 ];
//		dim[ 1 ] = tmp[ 1 ];
//		dim[ 2 ] = tmp[ 3 ];
//		dim[ 3 ] = tmp[ 2 ];

		long[] dim = Intervals.dimensionsAsLongArray( posField );
		FloatImagePlus< FloatType > displacement = ImagePlusImgs.floats( dim );

		if( dim_component < 0 )
			position2Displacement( posField, displacement );
		else
			position2Displacement( dim_component, posField, displacement );

//		IntervalView< FloatType > dispPerm = Views.permute( displacement, 2, 3 );
//		System.out.println( "posField    : " + Util.printInterval( posField ));
//		System.out.println( "displacement: " + Util.printInterval( displacement ));
//		System.out.println( "distPerm    : " + Util.printInterval( dispPerm ));
//		position2Displacement( posField, dispPerm );

		IJ.save( displacement.getImagePlus(), outArg + suffix + ".tif" );
	}

	/**
	 * Converts a position field to a displacement field.
	 * 
	 * @param posRai the position component of dimension 'dim'
	 * @param disRai the displacement field
	 * @param dim the dimension
	 */
	public static <T extends RealType< T >> void position2Displacement(
			int dim, 
			RandomAccessibleInterval< T > posRai, RandomAccessibleInterval< T > disRai )
	{

		RandomAccess< T > pra = posRai.randomAccess();
		Cursor< T > c = Views.flatIterable( disRai ).cursor();
		while ( c.hasNext() )
		{
			c.fwd();
			pra.setPosition( c );

			double dst = pra.get().getRealDouble();
			if( dst == 0.0 )
				continue;

			double src = c.getDoublePosition( dim );
			c.get().setReal( dst - src );
		}
	}

	/**
	 * Converts a position field to a displacement field.
	 * 
	 * @param posRai the position field
	 * @param disRai the displacement field
	 */
	public static <T extends RealType< T >> void position2Displacement(
			RandomAccessibleInterval< T > posRai, RandomAccessibleInterval< T > disRai )
	{
		int nd = posRai.numDimensions();

		RandomAccess< T > pra = posRai.randomAccess();
		Cursor< T > c = Views.flatIterable( disRai ).cursor();
		while ( c.hasNext() )
		{
			c.fwd();
			pra.setPosition( c );

			double dst = pra.get().getRealDouble();
			if( dst == 0.0 )
				continue;

			double src = c.getDoublePosition( c.getIntPosition( nd - 1 ) );
			c.get().setReal( dst - src );
		}
	}
	
	
	public static Img<FloatType> read( String filePath )
	{
		if( filePath.endsWith( "nii" ))
		{
			try
			{
				return ImageJFunctions.convertFloat( 
						NiftiIo.readNifti( new File( filePath ) ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			return ImageJFunctions.convertFloat( IJ.openImage( filePath ));
		}	
		return null;
	}
}
