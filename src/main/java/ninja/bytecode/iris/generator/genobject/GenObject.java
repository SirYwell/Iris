package ninja.bytecode.iris.generator.genobject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Directional;
import org.bukkit.material.MaterialData;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import ninja.bytecode.iris.util.Direction;
import ninja.bytecode.iris.util.MB;
import ninja.bytecode.iris.util.VectorMath;
import ninja.bytecode.shuriken.collections.GList;
import ninja.bytecode.shuriken.collections.GMap;
import ninja.bytecode.shuriken.io.CustomOutputStream;
import ninja.bytecode.shuriken.logging.L;

public class GenObject
{
	private boolean centeredHeight;
	private int w;
	private int h;
	private int d;
	private String name = "?";
	private final GMap<BlockVector, MB> s;
	private BlockVector mount;
	private int mountHeight;
	private BlockVector shift;

	public GenObject(int w, int h, int d)
	{
		this.w = w;
		this.h = h;
		this.d = d;
		shift = new BlockVector();
		s = new GMap<>();
		centeredHeight = false;
	}

	public void computeMountShift()
	{
		int ly = Integer.MAX_VALUE;

		for(BlockVector i : s.k())
		{
			if(i.getBlockY() < ly)
			{
				ly = i.getBlockY();
			}
		}

		GList<BlockVector> fmount = new GList<>();

		for(BlockVector i : s.k())
		{
			if(i.getBlockY() == ly)
			{
				fmount.add(i);
			}
		}

		double avx[] = new double[fmount.size()];
		double avy[] = new double[fmount.size()];
		double avz[] = new double[fmount.size()];
		int c = 0;

		for(BlockVector i : fmount)
		{
			avx[c] = i.getBlockX();
			avy[c] = i.getBlockY();
			avz[c] = i.getBlockZ();
			c++;
		}

		mountHeight = avg(avy);
		mount = new BlockVector(avg(avx), 0, avg(avz));
	}

	private int avg(double[] v)
	{
		double g = 0;

		for(int i = 0; i < v.length; i++)
		{
			g += v[i];
		}

		return (int) Math.round(g / (double) v.length);
	}

	public void setCenteredHeight()
	{
		this.centeredHeight = true;
	}

	public int getW()
	{
		return w;
	}

	public int getH()
	{
		return h;
	}

	public int getD()
	{
		return d;
	}

	public GMap<BlockVector, MB> getSchematic()
	{
		return s;
	}

	@SuppressWarnings("deprecation")
	public void read(InputStream in) throws IOException
	{
		GZIPInputStream gzi = new GZIPInputStream(in);
		DataInputStream din = new DataInputStream(gzi);
		w = din.readInt();
		h = din.readInt();
		d = din.readInt();
		int l = din.readInt();
		clear();

		for(int i = 0; i < l; i++)
		{
			s.put(new BlockVector(din.readInt(), din.readInt(), din.readInt()), new MB(Material.getMaterial((int) din.readInt()), din.readInt()));
		}

		din.close();
	}

	@SuppressWarnings("deprecation")
	public void write(OutputStream out) throws IOException
	{
		CustomOutputStream cos = new CustomOutputStream(out, 9);
		DataOutputStream dos = new DataOutputStream(cos);
		dos.writeInt(w);
		dos.writeInt(h);
		dos.writeInt(d);
		dos.writeInt(s.size());

		for(BlockVector i : s.keySet())
		{
			dos.writeInt(i.getBlockX());
			dos.writeInt(i.getBlockY());
			dos.writeInt(i.getBlockZ());
			dos.writeInt(s.get(i).material.getId());
			dos.writeInt(s.get(i).data);
		}

		dos.close();
	}

	public MB get(int x, int y, int z)
	{
		return s.get(new BlockVector(x, y, z));
	}

	public boolean has(int x, int y, int z)
	{
		return s.contains(new BlockVector(x, y, z));
	}

	public void put(int x, int y, int z, MB mb)
	{
		s.put(new BlockVector(x, y, z), mb);
	}

	public GenObject copy()
	{
		GenObject s = new GenObject(w, h, d);
		s.fill(this.s);
		s.centeredHeight = centeredHeight;
		s.name = name;
		return s;
	}

	public void clear()
	{
		s.clear();
	}

	public void fill(GMap<BlockVector, MB> b)
	{
		clear();
		s.put(b);
	}

	public int sh(int g)
	{
		int m = (g / 2);
		return g % 2 == 0 ? m : m + 1;
	}

	@SuppressWarnings("deprecation")
	public void place(World source, int wx, int wy, int wz)
	{
		Location start = new Location(source, wx, wy, wz).clone().add(sh(w), sh(h) + 1, sh(d));
		start.subtract(mount);
		int highestY = source.getHighestBlockYAt(start);

		if(start.getBlockY() + mountHeight > highestY)
		{
			start.subtract(0, start.getBlockY() + mountHeight - highestY, 0);
		}

		start.add(shift);
		GMap<Location, MB> undo = new GMap<>();

		for(BlockVector i : getSchematic().k())
		{
			MB b = getSchematic().get(i);
			Location f = start.clone().add(i);

			if(i.getBlockY() == mountHeight && f.clone().subtract(0, 1, 0).getBlock().isLiquid())
			{
				for(Location j : undo.k())
				{
					source.getBlockAt(j.getBlockX(), j.getBlockY(), j.getBlockZ()).setTypeIdAndData(undo.get(j).material.getId(), undo.get(j).data, false);
				}

				return;
			}

			if(b.material.equals(Material.SKULL))
			{
				continue;
			}

			try
			{
				undo.put(f, MB.of(f.getBlock().getType(), f.getBlock().getData()));
				source.getBlockAt(f.getBlockX(), f.getBlockY(), f.getBlockZ()).setTypeIdAndData(b.material.getId(), b.data, false);
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}
	}

	public static GenObject load(InputStream in) throws IOException
	{
		GenObject s = new GenObject(1, 1, 1);
		s.read(in);

		return s;
	}

	public static GenObject load(File f) throws IOException
	{
		GenObject s = new GenObject(1, 1, 1);
		s.name = f.getName().replaceAll("\\Q.ish\\E", "");
		FileInputStream fin = new FileInputStream(f);
		s.read(fin);

		return s;
	}

	public String getName()
	{
		return name;
	}

	public void rotate(Direction from, Direction to)
	{
		GMap<BlockVector, MB> g = s.copy();
		s.clear();

		for(BlockVector i : g.k())
		{
			MB mb = rotate(from, to, g.get(i));
			s.put(VectorMath.rotate(from, to, i).toBlockVector(), mb);
		}

		name = name + "-rt" + to.name();
	}

	@SuppressWarnings("deprecation")
	private MB rotate(Direction from, Direction to, MB mb)
	{
		Class<? extends MaterialData> cl = mb.material.getData();

		if(cl.isAssignableFrom(Directional.class))
		{
			try
			{
				Directional d = (Directional) cl.getConstructor(int.class, byte.class).newInstance(mb.material.getId(), mb.data);
				BlockFace f = d.getFacing();
				Vector mod = new Vector(f.getModX(), f.getModY(), f.getModZ());
				Vector modded = VectorMath.rotate(from, to, mod);

				for(BlockFace i : BlockFace.values())
				{
					if(i.getModX() == modded.getBlockX() && i.getModY() == modded.getBlockY() && i.getModZ() == modded.getBlockZ())
					{
						d.setFacingDirection(i);
						return new MB(mb.material, ((MaterialData) d).getData());
					}
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		return mb;
	}

	public void computeFlag(String j)
	{
		try
		{
			if(j.startsWith("sink="))
			{
				int downshift = Integer.valueOf(j.split("\\Q=\\E")[1]);
				shift.subtract(new Vector(0, downshift, 0));
			}
		}

		catch(Throwable e)
		{
			L.f("Failed to compute flag '" + j + "'");
			L.ex(e);
		}
	}
}
