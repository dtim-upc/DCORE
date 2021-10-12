#!/usr/bin/env stack
-- stack --resolver lts-18.13 script --package turtle --package foldl
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE DerivingStrategies #-}
{-# LANGUAGE ViewPatterns #-}

-----------------------------------------

import Turtle
import Prelude hiding (FilePath)
import qualified Control.Foldl as Fold
import Data.Coerce
import Data.Maybe (mapMaybe)
import Data.Foldable(for_)

-----------------------------------------

newtype Complexity = Complexity { unComplexity :: Text }
  deriving newtype (Show, Eq, Read, IsString)

linear :: Complexity
linear = "linear"

quadratic :: Complexity
quadratic = "quadratic"

cubic :: Complexity
cubic = "cubic"

-----------------------------------------

newtype Strategy = Strategy { unStrategy :: Text }
  deriving newtype (Show, Eq, Read, IsString)

roundRobin :: Strategy
roundRobin = "RoundRobin"

sequential :: Strategy
sequential = "Sequential"

-----------------------------------------

fpToText :: FilePath -> Text
fpToText fp =
  case toText fp of
    Left approx -> error "fpToText"
    Right fp -> fp

-- | >>> getBenchDir "benchmark/"
-- ["benchmark0"]
getBenchDir :: MonadIO m => FilePath -> m [FilePath]
getBenchDir root = do
  fps <- fold (ls root) Fold.list
  return $ coerce (mapMaybe (toBenchmark . basename) fps)
  where
    pat :: Pattern Integer
    pat = "benchmark" >> decimal

    toBenchmark :: FilePath -> Maybe FilePath
    toBenchmark (fpToText -> fp) =
      case match pat fp of
        [] -> Nothing
        _  -> Just (fromText fp)

-----------------------------------------

newtype Query = Query { unQuery :: Text }
  deriving newtype (Show, Eq, Read, IsString)

-- | >>> getQueries "benchmark/benchmark0"
-- ["query5","query4","query1","query2","query3"]
getQueries :: MonadIO m => FilePath -> m [Query]
getQueries benchmark = do
  fps <- fold (ls benchmark) Fold.list
  return $ coerce (mapMaybe (toQuery . basename) fps)
  where
    pat :: Pattern Integer
    pat = "query" >> decimal

    toQuery :: FilePath -> Maybe Query
    toQuery (fpToText -> fp) =
      case match pat fp of
        [] -> Nothing
        _  -> Just (coerce fp)

-----------------------------------------

newtype Benchmark = Benchmark { unBenchmark :: Text }
  deriving newtype (Show, Eq, Read, IsString)

formatBench :: Query -> Complexity -> Strategy -> Benchmark
formatBench query complexity strategy =
   coerce $
     format (s % "." % s % "." % s)
            (coerce query)
            (coerce complexity)
            (coerce strategy)

-----------------------------------------

-- Sbt is **really slow** to startup.
-- Bloop doesn't support sbt plugins AFAIK.

runSbt :: MonadIO m => Text -> m ()
runSbt subCmd = do
  printf ("sbt> " % s % "\n" ) subCmd
  shells (format ("sbt \"" % s %  "\"") subCmd) empty

runBenchmark :: MonadIO m => Benchmark -> m ()
runBenchmark = runSbt . toBenchmarkCmd . coerce where
  toBenchmarkCmd = format ("benchmark/multi-jvm:run " % s)

-----------------------------------------

main :: IO ()
main = do
  let rootDir = "benchmark/" :: FilePath
  benchmarks <- getBenchDir rootDir
  for_ benchmarks $ \benchmark -> do
    queries <- getQueries (rootDir </> benchmark)
    for_ queries $ \query -> do
      -- for_ [linear, quadratic, cubic] $ \complexity -> do
      for_ [linear] $ \complexity -> do
        for_ [sequential, roundRobin] $ \strategy -> do
          let benchmark = formatBench query complexity strategy
          -- print benchmark
          runBenchmark benchmark
  echo "Finished"
