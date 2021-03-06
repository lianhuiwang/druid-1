/*
 * Druid - a distributed column store.
 * Copyright (C) 2012  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.metamx.druid.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.metamx.druid.jackson.DefaultObjectMapper;
import com.metamx.druid.shard.NoneShardSpec;
import com.metamx.druid.shard.SingleDimensionShardSpec;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 */
public class DataSegmentTest
{
  final ObjectMapper mapper = new DefaultObjectMapper();

  @Test
  public void testV1Serialization() throws Exception
  {

    final Interval interval = new Interval("2011-10-01/2011-10-02");
    final ImmutableMap<String, Object> loadSpec = ImmutableMap.<String, Object>of("something", "or_other");

    DataSegment segment = new DataSegment(
        "something",
        interval,
        "1",
        loadSpec,
        Arrays.asList("dim1", "dim2"),
        Arrays.asList("met1", "met2"),
        new NoneShardSpec(),
        1
    );

    final Map<String, Object> objectMap = mapper.readValue(mapper.writeValueAsString(segment), new TypeReference<Map<String, Object>>(){});

    Assert.assertEquals(9, objectMap.size());
    Assert.assertEquals("something", objectMap.get("dataSource"));
    Assert.assertEquals(interval.toString(), objectMap.get("interval"));
    Assert.assertEquals("1", objectMap.get("version"));
    Assert.assertEquals(loadSpec, objectMap.get("loadSpec"));
    Assert.assertEquals("dim1,dim2", objectMap.get("dimensions"));
    Assert.assertEquals("met1,met2", objectMap.get("metrics"));
    Assert.assertEquals(ImmutableMap.of("type", "none"), objectMap.get("shardSpec"));
    Assert.assertEquals(1, objectMap.get("size"));

    DataSegment deserializedSegment = mapper.readValue(mapper.writeValueAsString(segment), DataSegment.class);

    Assert.assertEquals(segment.getDataSource(), deserializedSegment.getDataSource());
    Assert.assertEquals(segment.getInterval(), deserializedSegment.getInterval());
    Assert.assertEquals(segment.getVersion(), deserializedSegment.getVersion());
    Assert.assertEquals(segment.getLoadSpec(), deserializedSegment.getLoadSpec());
    Assert.assertEquals(segment.getDimensions(), deserializedSegment.getDimensions());
    Assert.assertEquals(segment.getMetrics(), deserializedSegment.getMetrics());
    Assert.assertEquals(segment.getShardSpec(), deserializedSegment.getShardSpec());
    Assert.assertEquals(segment.getSize(), deserializedSegment.getSize());
    Assert.assertEquals(segment.getIdentifier(), deserializedSegment.getIdentifier());

    deserializedSegment = mapper.readValue(mapper.writeValueAsString(segment), DataSegment.class);
    Assert.assertEquals(0, segment.compareTo(deserializedSegment));

    deserializedSegment = mapper.readValue(mapper.writeValueAsString(segment), DataSegment.class);
    Assert.assertEquals(0, deserializedSegment.compareTo(segment));

    deserializedSegment = mapper.readValue(mapper.writeValueAsString(segment), DataSegment.class);
    Assert.assertEquals(segment.hashCode(), deserializedSegment.hashCode());
  }

  @Test
  public void testIdentifier()
  {
    final DataSegment segment = DataSegment.builder()
                                           .dataSource("foo")
                                           .interval(new Interval("2012-01-01/2012-01-02"))
                                           .version(new DateTime("2012-01-01T11:22:33.444Z").toString())
                                           .shardSpec(new NoneShardSpec())
                                           .build();

    Assert.assertEquals(
        "foo_2012-01-01T00:00:00.000Z_2012-01-02T00:00:00.000Z_2012-01-01T11:22:33.444Z",
        segment.getIdentifier()
    );
  }

  @Test
  public void testIdentifierWithZeroPartition()
  {
    final DataSegment segment = DataSegment.builder()
                                           .dataSource("foo")
                                           .interval(new Interval("2012-01-01/2012-01-02"))
                                           .version(new DateTime("2012-01-01T11:22:33.444Z").toString())
                                           .shardSpec(new SingleDimensionShardSpec("bar", null, "abc", 0))
                                           .build();

    Assert.assertEquals(
        "foo_2012-01-01T00:00:00.000Z_2012-01-02T00:00:00.000Z_2012-01-01T11:22:33.444Z",
        segment.getIdentifier()
    );
  }

  @Test
  public void testIdentifierWithNonzeroPartition()
  {
    final DataSegment segment = DataSegment.builder()
                                           .dataSource("foo")
                                           .interval(new Interval("2012-01-01/2012-01-02"))
                                           .version(new DateTime("2012-01-01T11:22:33.444Z").toString())
                                           .shardSpec(new SingleDimensionShardSpec("bar", "abc", "def", 1))
                                           .build();

    Assert.assertEquals(
        "foo_2012-01-01T00:00:00.000Z_2012-01-02T00:00:00.000Z_2012-01-01T11:22:33.444Z_1",
        segment.getIdentifier()
    );
  }

  @Test
  public void testV1SerializationNullMetrics() throws Exception
  {
    final DataSegment segment = DataSegment.builder()
                                           .dataSource("foo")
                                           .interval(new Interval("2012-01-01/2012-01-02"))
                                           .version(new DateTime("2012-01-01T11:22:33.444Z").toString())
                                           .build();

    final DataSegment segment2 = mapper.readValue(mapper.writeValueAsString(segment), DataSegment.class);
    Assert.assertEquals("empty dimensions", ImmutableList.of(), segment2.getDimensions());
    Assert.assertEquals("empty metrics", ImmutableList.of(), segment2.getMetrics());
  }

  @Test
  public void testBucketMonthComparator() throws Exception
  {
    DataSegment[] sortedOrder = {
        makeDataSegment("test1", "2011-01-01/2011-01-02", "a"),
        makeDataSegment("test1", "2011-01-02/2011-01-03", "a"),
        makeDataSegment("test1", "2011-01-02/2011-01-03", "b"),
        makeDataSegment("test2", "2011-01-01/2011-01-02", "a"),
        makeDataSegment("test2", "2011-01-02/2011-01-03", "a"),
        makeDataSegment("test1", "2011-02-01/2011-02-02", "a"),
        makeDataSegment("test1", "2011-02-02/2011-02-03", "a"),
        makeDataSegment("test1", "2011-02-02/2011-02-03", "b"),
        makeDataSegment("test2", "2011-02-01/2011-02-02", "a"),
        makeDataSegment("test2", "2011-02-02/2011-02-03", "a"),
    };

    List<DataSegment> shuffled = Lists.newArrayList(sortedOrder);
    Collections.shuffle(shuffled);

    Set<DataSegment> theSet = Sets.newTreeSet(DataSegment.bucketMonthComparator());
    theSet.addAll(shuffled);

    int index = 0;
    for (DataSegment dataSegment : theSet) {
      Assert.assertEquals(sortedOrder[index], dataSegment);
      ++index;
    }
  }

  private DataSegment makeDataSegment(String dataSource, String interval, String version)
  {
    return DataSegment.builder()
                      .dataSource(dataSource)
                      .interval(new Interval(interval))
                      .version(version)
                      .size(1)
                      .build();
  }
}
