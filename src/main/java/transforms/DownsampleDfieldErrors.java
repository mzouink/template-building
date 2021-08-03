package transforms;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import bigwarp.BigWarpExporter;
import evaluation.TransformComparison;
import io.DfieldIoHelper;
import io.WriteH5DisplacementField;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import transforms.DownsampleDfield.DownsamplingMethod;

@Command( version = "0.1.1-SNAPSHOT")
public class DownsampleDfieldErrors implements Callable<Void>
{

	@Option( names = {"-i","--input"}, required = true, description = "Displacement field path" )
	private String fieldPath;

	@Option( names = {"-f", "--factors"}, required = true,  split = ",", description = "Downsampling factors" )
	private long[] factors;

	@Option( names = {"-c", "--convertType"}, required = false, description = "Type conversion (short,byte)" )
	private String type = "";

	@Option( names = {"-m", "--method"}, required = false, 
			description = "Downsampling method (sample,average,gaussian). default=\"gaussian\"" )
	private String method = "gaussian";

	@Option( names = {"-j", "--nThreads"}, required = false, description = "Number of threads" )
	private int nThreads = 4;

	public static void main( String[] args )
	{
		CommandLine.call( new DownsampleDfieldErrors(), args );
		System.exit(0);
	}

	public Void call()
	{
		DfieldIoHelper dio = new DfieldIoHelper();
		RandomAccessibleInterval<FloatType> dfield;
		try {
			dfield = dio.read(fieldPath);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
//		BdvFunctions.show( dfield, "df" );
		
//		System.out.println( dfield );
//		System.out.println( Util.printInterval( dfield ));
//		
//		IntervalView< FloatType > dx = Views.hyperSlice( dfield, 3, 0 );
//		IntervalView< FloatType > dy = Views.hyperSlice( dfield, 3, 1 );
//		IntervalView< FloatType > dz = Views.hyperSlice( dfield, 3, 2 );
//
//		int z = 155;
//		
//		IntervalView< FloatType > dxSlc = Views.hyperSlice( dx, 2, z );
//		IntervalView< FloatType > dySlc = Views.hyperSlice( dy, 2, z );
//		IntervalView< FloatType > dzSlc = Views.hyperSlice( dz, 2, z );
//		
//		IntervalView< FloatType > dxSlc2 = Views.hyperSlice( dx, 2, z+1 );
//		IntervalView< FloatType > dySlc2 = Views.hyperSlice( dy, 2, z+1 );
//		IntervalView< FloatType > dzSlc2 = Views.hyperSlice( dz, 2, z+1 );
//		
//		ImageJFunctions.show( dxSlc );
//		ImageJFunctions.show( dxSlc2 );
		
//		ImageJFunctions.show( dySlc );
//		ImageJFunctions.show( dzSlc );

		estimateDfieldErrors( dfield, factors, dio.spacing, type, method, nThreads );

		return null;
	}
	
	public static void estimateDfieldErrors(
			RandomAccessibleInterval<FloatType> dfield,
			long[] subsample_factors,
			double[] resIn,
			final String type,
			final String method,
			int nThreads )
	{
//		RandomAccessibleInterval< T > dfieldSubInterp = buildSampledDfield( dfield, subsample_factors );
//		compare( dfield, dfieldSubInterp );
		
//		RandomAccessibleInterval< FloatType > dfieldSub = buildSampledDfield( dfield, subsample_factors, true );
//		compare( dfield, dfieldSub, 16 );
//		compare( dfield, dfieldSub, 4 );
//		compare( dfield, dfieldSub );

		RandomAccessibleInterval< FloatType > dfieldSub = buildSampledDfield( dfield, subsample_factors, method, nThreads );


		if( type.equals("byte"))
			compare( dfield, convertByte( dfieldSub ), nThreads );
		else if( type.equals("short"))
			compare( dfield, convertThruShort( dfieldSub ), nThreads );
		else
			compare( dfield, dfieldSub, nThreads );
		
//		compare( dfield, dfieldSub, 4 );
//		compare( dfield, dfieldSub );
		
//		double[] resOut = new double[ 3 ];
//		resOut[ 0 ] = resIn[ 0 ] * subsample_factors[ 0 ];
//		resOut[ 1 ] = resIn[ 1 ] * subsample_factors[ 1 ];
//		resOut[ 2 ] = resIn[ 2 ] * subsample_factors[ 2 ];
		
//		RandomAccessibleInterval< FloatType > dfieldDownAvg = downsampleAverage( dfield, subsample_factors, 16 );
//		try
//		{
//			ImagePlus ip = ((ImagePlusImg<?,?>)dfieldDownAvg).getImagePlus();
//			ip.getCalibration().pixelWidth = resOut[ 0 ];
//			ip.getCalibration().pixelHeight = resOut[ 1 ];
//			ip.getCalibration().pixelDepth = resOut[ 2 ];
//
//			WritingHelper.write( ip, 
//					"/groups/saalfeld/public/jrc2018/transformations/jrc2018U-jrc2018F/jrc2018U-jrc2018F_Warp_down222.nii");
//		} catch ( ImgLibException e )
//		{
//			e.printStackTrace();
//		}
	}

	/**
	 * @deprecated
	 * use DownsampleDfield.downsampleDisplacementFieldAverage
	 * 
	 * @param <T>
	 * @param dfield
	 * @param subsample_factors
	 * @return
	 */
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> downsampleAverage(
			RandomAccessibleInterval<T> dfield,
			long[] subsample_factors )
	{
	
		ArrayImg< FloatType, FloatArray > out = ArrayImgs.floats( 
				Intervals.dimensionsAsLongArray( Views.subsample( dfield, subsample_factors )));
		
		RandomAccess< T > dfRa = Views.extendMirrorDouble( dfield ).randomAccess();
		
		IntervalIterator it = new IntervalIterator( subsample_factors );
		
		ArrayCursor< FloatType > c = out.cursor();
		while( c.hasNext() )
		{
			double avgval = 0;
			
			c.fwd();
			for( int d = 0; d < dfRa.numDimensions(); d++ )
				dfRa.setPosition( subsample_factors[ d ] * c.getLongPosition( d ), d );
			
			it.reset();
			while( it.hasNext() )
			{
				it.fwd();
				
				for( int d = 0; d < dfRa.numDimensions(); d++ )
					dfRa.setPosition( subsample_factors[ d ] * c.getLongPosition( d ), d );
			
				dfRa.move( it );
				avgval += dfRa.get().getRealDouble();
			}
			c.get().setReal( avgval );
		}
		
		return out;
	}
	
	/**
	 * use DownsampleDfield.downsampleDisplacementFieldAverage
	 */
	@Deprecated
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> downsampleAverage(
			final RandomAccessibleInterval<T> dfield,
			final long[] subsample_factors,
			final int nThreads )
	{
//		long[] interval = Intervals.dimensionsAsLongArray( Views.subsample( dfield, subsample_factors ));
		long[] interval = DownsampleDfield.resampledDfieldSize( dfield, subsample_factors );

		FloatImagePlus< FloatType > outRaw = ImagePlusImgs.floats( 
				interval[0], interval[1], interval[3], interval[2]);

		IntervalView< FloatType > out = Views.permute( outRaw, 2, 3 );

		System.out.println( "out sz : " + Util.printInterval( out ));
		
//		ArrayImg< FloatType, FloatArray > out = ArrayImgs.floats( 
//				Intervals.dimensionsAsLongArray( Views.subsample( dfield, subsample_factors )));
		
//		final long[] dimensions = new long[ 4 ];
//		dimensions[ 0 ] = out.dimension( 0 );	// x
//		dimensions[ 1 ] = out.dimension( 1 );	// y
//		dimensions[ 2 ] = out.dimension( 2 ); 	// z
//		dimensions[ 3 ] = out.dimension( 3 ); 	// v 
//		FinalInterval destIntervalPerm = new FinalInterval( dimensions );
		
		final int dim2split = 2;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = out.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = out.dimension( dim2split );
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
		}
		System.out.println( "dim2split: " + dim2split );
		System.out.println( "split points: " + Arrays.toString( splitPoints ));

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					final IntervalIterator it = new IntervalIterator( subsample_factors );
					final RandomAccess< T > dfRa = Views.extendMirrorDouble( dfield ).randomAccess();
					
					double intervalPixelCount = subsample_factors[ 0 ] * subsample_factors[ 1 ] * subsample_factors[ 2 ]; 
					
					final FinalInterval subItvl = BigWarpExporter.getSubInterval( out, dim2split, start, end );
					final IntervalView< FloatType > subTgt = Views.interval( out, subItvl );
					final Cursor< FloatType > c = subTgt.cursor();
					
//					System.out.println( "subtgt: " + Util.printInterval( subTgt ));

					while( c.hasNext() )
					{
//						System.out.println( "c pos: " + Util.printCoordinates( c ));
						double avgval = 0;
						
						c.fwd();
						for( int d = 0; d < dfRa.numDimensions(); d++ )
							dfRa.setPosition( subsample_factors[ d ] * c.getLongPosition( d ), d );
						
						it.reset();
						while( it.hasNext() )
						{
							it.fwd();
							for( int d = 0; d < dfRa.numDimensions(); d++ )
								dfRa.setPosition( subsample_factors[ d ] * c.getLongPosition( d ), d );

							dfRa.move( it );
							avgval += dfRa.get().getRealDouble();
						}
						c.get().setReal( avgval / intervalPixelCount );
					}
					
					return true;
				}
			});
		}
		
		try
		{
			List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		}
		
		return outRaw;
	}

	public static <T extends RealType<T>> RandomAccessibleInterval<T> downsampleSample(
			RandomAccessibleInterval<T> dfield,
			long[] subsample_factors )
	{
		return Views.subsample( dfield, subsample_factors );
	}

	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> convertThruByte(
			RandomAccessibleInterval<T> dfield )
	{
		System.out.println( "CONVERT THROUGH SHORT" );
		FloatType s = new FloatType();
		double maxValue = WriteH5DisplacementField.getMaxAbs( Views.iterable( dfield ));
		
		final double m = WriteH5DisplacementField.getMultiplier( s, maxValue );

		return Converters.convert(
				dfield, 
				new Converter<T, FloatType>()
				{
					final ByteType tmp = new ByteType();
					
					@Override
					public void convert(T input, FloatType output) {
						tmp.setReal( input.getRealDouble() * m );
						output.setReal( tmp.getRealDouble() / m );
//						double diff = output.getRealDouble() - input.getRealDouble();
//						if( diff * diff > 100 )
//						{
//							System.out.println( "uh oh");
//							System.out.println( input.getRealDouble() );
//							System.out.println( output.getRealDouble() );
//						}
					}
				}, 
				s);
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval<ByteType> convertByte(
			RandomAccessibleInterval<T> dfield )
	{
		System.out.println( "CONVERT BYTE" );
		ByteType t = new ByteType();
		double maxValue = WriteH5DisplacementField.getMaxAbs( Views.iterable( dfield ));
		final double m = WriteH5DisplacementField.getMultiplier( t, maxValue );

		return Converters.convert(
				dfield, 
				new Converter<T, ByteType>()
				{
					@Override
					public void convert(T input, ByteType output) {
						output.setReal( input.getRealDouble() * m );
					}
				}, 
				t);
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> convertThruShort(
			RandomAccessibleInterval<T> dfield )
	{
		System.out.println( "CONVERT THROUGH SHORT" );
		FloatType s = new FloatType();
		double maxValue = WriteH5DisplacementField.getMaxAbs( Views.iterable( dfield ));
		
		final double m = WriteH5DisplacementField.getMultiplier( s, maxValue );

		return Converters.convert(
				dfield, 
				new Converter<T, FloatType>()
				{
					final ShortType tmp = new ShortType();
					
					@Override
					public void convert(T input, FloatType output) {
						tmp.setReal( input.getRealDouble() * m );
						output.setReal( tmp.getRealDouble() / m );
//						double diff = output.getRealDouble() - input.getRealDouble();
//						if( diff * diff > 100 )
//						{
//							System.out.println( "uh oh");
//							System.out.println( input.getRealDouble() );
//							System.out.println( output.getRealDouble() );
//						}
					}
				}, 
				s);
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval<ShortType> convertShort(
			RandomAccessibleInterval<T> dfield )
	{
		System.out.println( "CONVERT SHORT" );
		ShortType s = new ShortType();
		double maxValue = WriteH5DisplacementField.getMaxAbs( Views.iterable( dfield ));
		
		final double m = WriteH5DisplacementField.getMultiplier( s, maxValue );

		return Converters.convert(
				dfield, 
				new Converter<T, ShortType>()
				{
					@Override
					public void convert(T input, ShortType output) {
						output.setReal( input.getRealDouble() * m );
//						double diff = output.getRealDouble() - input.getRealDouble();
//						if( diff * diff > 100 )
//						{
//							System.out.println( "uh oh");
//							System.out.println( input.getRealDouble() );
//							System.out.println( output.getRealDouble() );
//						}
					}
				}, 
				s);
	}

	public static RandomAccessibleInterval<FloatType> buildSampledDfield(
			RandomAccessibleInterval<FloatType> dfield,
			long[] subsample_factors,
			String method,
			int nThreads )
	{
		System.out.println( "dfield : " + Util.printInterval( dfield ));

		RandomAccessibleInterval<FloatType> dfieldSub;
		if( method.equals("average") )
			dfieldSub = DownsampleDfield.downsampleDisplacementFieldAverage( dfield, subsample_factors, nThreads );
		else
			dfieldSub = downsampleSample( dfield, subsample_factors );

		System.out.println( "subs   : " + Util.printInterval( dfieldSub ));
		
		AffineTransform dfieldToPhysical = new AffineTransform( 4 );
		dfieldToPhysical.set( subsample_factors[0], 0, 0 );
		dfieldToPhysical.set( subsample_factors[1], 1, 1 );
		dfieldToPhysical.set( subsample_factors[2], 2, 2 );

		IntervalView< FloatType > dfieldSubInterp = Views.interval( 
			Views.raster( 
				RealViews.affine( 
					Views.interpolate( 
						Views.extendZero( dfieldSub ),
						new NLinearInterpolatorFactory< FloatType >()),
				dfieldToPhysical )),
			dfield);
		
		return dfieldSubInterp;
	}

	public static RandomAccessibleInterval<FloatType> buildSampledDfield(
			RandomAccessibleInterval<FloatType> dfield,
			long[] subsample_factors,
			DownsamplingMethod method,
			int nThreads )
	{
		System.out.println( "dfield : " + Util.printInterval( dfield ));

		RandomAccessibleInterval<FloatType> dfieldSub;
		if( method.equals("average") )
			dfieldSub = DownsampleDfield.downsampleDisplacementFieldAverage( dfield, subsample_factors, nThreads );
		else
			dfieldSub = downsampleSample( dfield, subsample_factors );

		System.out.println( "subs   : " + Util.printInterval( dfieldSub ));
		
		AffineTransform dfieldToPhysical = new AffineTransform( 4 );
		dfieldToPhysical.set( subsample_factors[0], 0, 0 );
		dfieldToPhysical.set( subsample_factors[1], 1, 1 );
		dfieldToPhysical.set( subsample_factors[2], 2, 2 );
		
		IntervalView< FloatType > dfieldSubInterp = Views.interval( 
			Views.raster( 
				RealViews.affine( 
					Views.interpolate( 
						Views.extendZero( dfieldSub ),
						new NLinearInterpolatorFactory< FloatType >()),
				dfieldToPhysical )),
			dfield);
		
		return dfieldSubInterp;
	}

	public static <T extends RealType<T>,S extends RealType<S>> void compare( 
			RandomAccessibleInterval<T> dfieldTrue,
			RandomAccessibleInterval<S> dfieldApprox,
			int nThreads ) 
	{
		
		final int dim2split = 2;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = dfieldTrue.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = dfieldTrue.dimension( dim2split );
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
		}
		System.out.println( "dim2split: " + dim2split );
		System.out.println( "split points: " + Arrays.toString( splitPoints ));

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList<Callable<SumMax>> jobs = new LinkedList<Callable<SumMax>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<SumMax>()
			{
				public SumMax call()
				{
					
					final FinalInterval subItvl = BigWarpExporter.getSubInterval( dfieldTrue, dim2split, start, end );
					final IntervalView< T > subTgt = Views.interval( dfieldTrue, subItvl );
					
//					System.out.println( "subtgt: " + Util.printInterval( subTgt ));
					double sum = 0;
					double max = Double.MIN_VALUE;
					
					RandomAccess< T > trueRa = dfieldTrue.randomAccess();
					RandomAccess< S > approxRa = dfieldApprox.randomAccess();
					
					// I know this will be a 3d
					IntervalIterator it = new IntervalIterator( 
							new long[]{
									subTgt.min( 0 ),
									subTgt.min( 1 ),
									subTgt.min( 2 ),
									0
							}, 
							new long[]{
									subTgt.max( 0 ),
									subTgt.max( 1 ),
									subTgt.max( 2 ),
									0
							} );


					double[] errV = new double[ 3 ];
					while( it.hasNext() )
					{
						it.fwd();
						trueRa.setPosition( it );
						approxRa.setPosition( it );
						
						err( errV, trueRa, approxRa );

						double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );
						sum += errMag;
						
						if( errMag > max )
							max = errMag;
					}
					return new SumMax( sum, max );
				}
			});
		}
		
		List< Future< SumMax > > futures = null;
		try
		{
			futures = threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
			return;
		}

		List<SumMax> results = futures.stream().map( x -> {
			try {
				return x.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			return new SumMax( 0, Double.MIN_VALUE );
		}).collect(Collectors.toList());

		double totalSum = results.stream().mapToDouble( x -> x.sum ).sum();
		OptionalDouble totalMax = results.stream().mapToDouble( x -> x.max ).max();

		long count = Arrays.stream( Intervals.dimensionsAsLongArray( dfieldTrue )).reduce( 1, (x,y) -> x*y );


		double avgErrMag = totalSum / (count/3);
		System.out.println( "avg error magnitude : " + avgErrMag );
		totalMax.ifPresent( x -> System.out.println( "max error magnitude : " + x ));
	}
	
	public static class SumMax
	{
		double sum;
		double max;
		public SumMax( double sum, double max ) {
			this.sum = sum;
			this.max = max;
		}
	}

	public static <T extends RealType<T>,S extends RealType<S>> void compare( 
			RandomAccessibleInterval<T> dfieldTrue,
			RandomAccessibleInterval<S> dfieldApprox ) 
	{
		
		RandomAccess< T > trueRa = dfieldTrue.randomAccess();
		RandomAccess< S > approxRa = dfieldApprox.randomAccess();
		
		// I know this will be a 3d
		IntervalIterator it = new IntervalIterator( 
				new long[]{
						dfieldTrue.min( 0 ),
						dfieldTrue.min( 1 ),
						dfieldTrue.min( 2 ),
						0
				}, 
				new long[]{
						dfieldTrue.max( 0 ),
						dfieldTrue.max( 1 ),
						dfieldTrue.max( 2 ),
						0
				} );

		double avgErrMag = 0;
		double minErrMag = Double.MAX_VALUE;
		double maxErrMag = Double.MIN_VALUE;

		long i = 0;
		double[] errV = new double[ 3 ];
		while( it.hasNext() )
		{
			it.fwd();
			trueRa.setPosition( it );
			approxRa.setPosition( it );
			
			err( errV, trueRa, approxRa );

			double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );
			avgErrMag += errMag;
			
			if( errMag < minErrMag )
				minErrMag = errMag;
			
			if( errMag > maxErrMag )
				maxErrMag = errMag;
			
			i++;
		}
		System.out.println( "total sum: " + avgErrMag );
		System.out.println( "n: " + i );
		
		avgErrMag /= i;

		System.out.println( "min eror magnitude : " + minErrMag );
		System.out.println( "avg error magnitude : " + avgErrMag );
		System.out.println( "max error magnitude : " + maxErrMag );
	}

	public static <T extends RealType<T>,S extends RealType<S>> void err( double[] err, RandomAccess<T> truth, RandomAccess<S> approx )
	{
		for( int d = 0; d < 3; d++ )
		{
			truth.setPosition( d, 3 );
			approx.setPosition( d, 3 );
			err[ d ] = truth.get().getRealDouble() - approx.get().getRealDouble();
		}
	}

}

